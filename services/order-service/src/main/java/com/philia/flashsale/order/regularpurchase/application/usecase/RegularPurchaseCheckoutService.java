package com.philia.flashsale.order.regularpurchase.application.usecase;

import com.philia.flashsale.order.order.application.port.out.CurrentTimePort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderIdentityPort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderNumberPort;
import com.philia.flashsale.order.order.domain.model.Order;
import com.philia.flashsale.order.order.domain.model.OrderLine;
import com.philia.flashsale.order.order.domain.model.PurchaseSource;
import com.philia.flashsale.order.order.domain.valueobject.Money;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSaga;
import com.philia.flashsale.order.regularpurchase.application.command.BuyNowCheckoutCommand;
import com.philia.flashsale.order.regularpurchase.application.exception.RegularPurchaseBusinessException;
import com.philia.flashsale.order.regularpurchase.application.exception.RegularPurchaseDownstreamException;
import com.philia.flashsale.order.regularpurchase.application.model.ProductPurchaseQuote;
import com.philia.flashsale.order.regularpurchase.application.model.RegularPurchaseAcceptance;
import com.philia.flashsale.order.regularpurchase.application.model.RegularStockHold;
import com.philia.flashsale.order.regularpurchase.application.model.RegularStockHoldCommand;
import com.philia.flashsale.order.regularpurchase.application.port.in.CheckoutBuyNowUseCase;
import com.philia.flashsale.order.regularpurchase.application.port.out.CreateRegularStockHoldPort;
import com.philia.flashsale.order.regularpurchase.application.port.out.LoadProductPurchaseQuotesPort;
import com.philia.flashsale.order.regularpurchase.application.port.out.PersistRegularPurchasePort;
import com.philia.flashsale.order.regularpurchase.application.result.RegularPurchaseCheckoutResult;
import com.philia.flashsale.order.regularpurchase.domain.model.IdempotencyMatch;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseLine;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseRequest;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseRequestState;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Resumable Buy Now orchestration. Each persistence call owns a short local transaction; no
 * Product or Inventory HTTP request is made while an Order transaction is open.
 */
public final class RegularPurchaseCheckoutService implements CheckoutBuyNowUseCase {

    private final PersistRegularPurchasePort persistence;
    private final LoadProductPurchaseQuotesPort productQuotes;
    private final CreateRegularStockHoldPort stockHolds;
    private final GenerateOrderIdentityPort identities;
    private final GenerateOrderNumberPort orderNumbers;
    private final CurrentTimePort clock;

    public RegularPurchaseCheckoutService(PersistRegularPurchasePort persistence,
            LoadProductPurchaseQuotesPort productQuotes, CreateRegularStockHoldPort stockHolds,
            GenerateOrderIdentityPort identities, GenerateOrderNumberPort orderNumbers, CurrentTimePort clock) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.productQuotes = Objects.requireNonNull(productQuotes, "productQuotes");
        this.stockHolds = Objects.requireNonNull(stockHolds, "stockHolds");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.orderNumbers = Objects.requireNonNull(orderNumbers, "orderNumbers");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public RegularPurchaseCheckoutResult checkout(BuyNowCheckoutCommand command) {
        Objects.requireNonNull(command, "command");
        Instant receivedAt = clock.now();
        RegularPurchaseLine submittedLine = new RegularPurchaseLine(command.variantId(), command.quantity(),
                command.expectedUnitPrice(), command.currency(), null);
        RegularPurchaseRequest candidate = RegularPurchaseRequest.receiveBuyNow(identities.generate(),
                command.shopperId(), command.idempotencyKey(), identities.generate(), identities.generate(),
                submittedLine, receivedAt);
        RegularPurchaseRequest intake = persistence.register(candidate);
        IdempotencyMatch match = intake.idempotencyMatch(command.shopperId(), command.idempotencyKey(),
                candidate.requestFingerprint());
        if (match == IdempotencyMatch.CONFLICT) {
            throw new RegularPurchaseBusinessException(
                    RegularPurchaseBusinessException.Reason.IDEMPOTENCY_KEY_REUSED);
        }
        if (intake.state() == RegularPurchaseRequestState.ACCEPTED) {
            return result(intake, true);
        }
        if (intake.state() == RegularPurchaseRequestState.REJECTED) {
            throw rejected(intake.rejectionCode());
        }

        if (intake.state() == RegularPurchaseRequestState.RECEIVED) {
            List<ProductPurchaseQuote> quotes = productQuotes.loadQuotes(
                    intake.lines().stream().map(RegularPurchaseLine::variantId).toList(), command.traceId());
            validateQuotes(intake, quotes);
            intake = persistence.update(intake.productValidated(clock.now()));
        }

        if (intake.state() == RegularPurchaseRequestState.PRODUCT_VALIDATED) {
            try {
                RegularStockHold hold = stockHolds.create(new RegularStockHoldCommand(intake.proposedHoldId(),
                        intake.id(), intake.proposedOrderId(), intake.shopperId(), clock.now(), intake.lines().stream()
                                .map(line -> new RegularStockHoldCommand.RegularStockHoldLine(
                                        line.variantId(), line.quantity()))
                                .toList()), command.traceId());
                intake = persistence.update(intake.holdAcquired(hold.expiresAt(), clock.now()));
            } catch (RegularPurchaseDownstreamException exception) {
                throw mapInventoryFailure(intake, exception);
            }
        }

        if (intake.state() != RegularPurchaseRequestState.HOLD_ACQUIRED) {
            throw new RegularPurchaseBusinessException(
                    RegularPurchaseBusinessException.Reason.PURCHASE_RECOVERY_REQUIRED);
        }
        Instant acceptedAt = clock.now();
        Order order = Order.regular(intake.proposedOrderId(), orderNumbers.generate(acceptedAt,
                intake.proposedOrderId()), intake.id(), PurchaseSource.BUY_NOW, intake.proposedHoldId(),
                intake.shopperId(), intake.currency(), intake.lines().stream().map(line -> OrderLine.create(
                        identities.generate(), line.variantId(), line.quantity(), line.expectedUnitPrice())).toList(),
                null, null, acceptedAt, intake.holdExpiresAt());
        PurchaseSaga saga = PurchaseSaga.startRegular(order.id(), intake.id(), intake.proposedHoldId(),
                intake.holdExpiresAt(), acceptedAt);
        RegularPurchaseRequest accepted = intake.accept(order.id(), acceptedAt);
        persistence.accept(new RegularPurchaseAcceptance(accepted, order, saga, identities.generate(),
                identities.generate(), intake.id(), intake.id(), acceptedAt, command.traceparent(),
                command.tracestate()));
        return result(accepted, false);
    }

    private void validateQuotes(RegularPurchaseRequest intake, List<ProductPurchaseQuote> quotes) {
        if (quotes == null || quotes.size() != intake.lines().size()) {
            throw new RegularPurchaseDownstreamException(
                    RegularPurchaseDownstreamException.Failure.PRODUCT_SERVICE_UNAVAILABLE);
        }
        for (RegularPurchaseLine line : intake.lines()) {
            ProductPurchaseQuote quote = quotes.stream().filter(item -> line.variantId().equals(item.variantId()))
                    .findFirst().orElseThrow(() -> new RegularPurchaseDownstreamException(
                            RegularPurchaseDownstreamException.Failure.PRODUCT_SERVICE_UNAVAILABLE));
            if (!quote.found()) {
                reject(intake, "VARIANT_NOT_FOUND");
                throw new RegularPurchaseBusinessException(
                        RegularPurchaseBusinessException.Reason.VARIANT_NOT_FOUND);
            }
            if (!quote.sellable()) {
                reject(intake, "VARIANT_NOT_SELLABLE");
                throw new RegularPurchaseBusinessException(
                        RegularPurchaseBusinessException.Reason.VARIANT_NOT_SELLABLE);
            }
            if (!line.expectedUnitPrice().equals(quote.unitPrice()) || !line.currency().equals(quote.currency())) {
                reject(intake, "PRICE_CHANGED");
                throw new RegularPurchaseBusinessException(
                        RegularPurchaseBusinessException.Reason.PRICE_CHANGED, quotes);
            }
        }
    }

    private void reject(RegularPurchaseRequest intake, String code) {
        persistence.update(intake.reject(code, clock.now()));
    }

    private RuntimeException mapInventoryFailure(RegularPurchaseRequest intake,
            RegularPurchaseDownstreamException exception) {
        return switch (exception.failure()) {
            case INVENTORY_INSUFFICIENT_STOCK -> {
                reject(intake, "INSUFFICIENT_STOCK");
                yield new RegularPurchaseBusinessException(
                        RegularPurchaseBusinessException.Reason.INSUFFICIENT_STOCK);
            }
            case INVENTORY_ITEM_NOT_FOUND -> {
                reject(intake, "INVENTORY_ITEM_NOT_FOUND");
                yield new RegularPurchaseBusinessException(
                        RegularPurchaseBusinessException.Reason.INVENTORY_ITEM_NOT_FOUND);
            }
            default -> exception;
        };
    }

    private RegularPurchaseBusinessException rejected(String code) {
        return switch (code) {
            case "VARIANT_NOT_FOUND" -> new RegularPurchaseBusinessException(
                    RegularPurchaseBusinessException.Reason.VARIANT_NOT_FOUND);
            case "VARIANT_NOT_SELLABLE" -> new RegularPurchaseBusinessException(
                    RegularPurchaseBusinessException.Reason.VARIANT_NOT_SELLABLE);
            case "PRICE_CHANGED" -> new RegularPurchaseBusinessException(
                    RegularPurchaseBusinessException.Reason.PRICE_CHANGED);
            case "INSUFFICIENT_STOCK" -> new RegularPurchaseBusinessException(
                    RegularPurchaseBusinessException.Reason.INSUFFICIENT_STOCK);
            case "INVENTORY_ITEM_NOT_FOUND" -> new RegularPurchaseBusinessException(
                    RegularPurchaseBusinessException.Reason.INVENTORY_ITEM_NOT_FOUND);
            default -> new RegularPurchaseBusinessException(
                    RegularPurchaseBusinessException.Reason.PURCHASE_RECOVERY_REQUIRED);
        };
    }

    private RegularPurchaseCheckoutResult result(RegularPurchaseRequest intake, boolean replayed) {
        Money total = intake.lines().stream().map(line -> line.expectedUnitPrice().multiply(line.quantity()))
                .reduce(Money::add).orElseThrow();
        return new RegularPurchaseCheckoutResult(intake.id(), intake.orderId(), intake.source(), "PENDING_PAYMENT",
                intake.currency(), total, intake.holdExpiresAt().minusSeconds(30), intake.holdExpiresAt(), replayed);
    }
}
