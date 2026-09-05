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
import com.philia.flashsale.order.regularpurchase.application.command.CartCheckoutCommand;
import com.philia.flashsale.order.regularpurchase.application.command.CartCheckoutLine;
import com.philia.flashsale.order.regularpurchase.application.exception.RegularPurchaseBusinessException;
import com.philia.flashsale.order.regularpurchase.application.exception.RegularPurchaseDownstreamException;
import com.philia.flashsale.order.regularpurchase.application.model.ProductPurchaseQuote;
import com.philia.flashsale.order.regularpurchase.application.model.RegularPurchaseAcceptance;
import com.philia.flashsale.order.regularpurchase.application.model.RegularStockHold;
import com.philia.flashsale.order.regularpurchase.application.model.RegularStockHoldCommand;
import com.philia.flashsale.order.regularpurchase.application.port.in.CheckoutBuyNowUseCase;
import com.philia.flashsale.order.regularpurchase.application.port.in.CheckoutCartUseCase;
import com.philia.flashsale.order.regularpurchase.application.port.out.CreateRegularStockHoldPort;
import com.philia.flashsale.order.regularpurchase.application.port.out.LoadCartCheckoutSnapshotPort;
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
public final class RegularPurchaseCheckoutService implements CheckoutBuyNowUseCase, CheckoutCartUseCase {

    private final PersistRegularPurchasePort persistence;
    private final LoadProductPurchaseQuotesPort productQuotes;
    private final CreateRegularStockHoldPort stockHolds;
    private final LoadCartCheckoutSnapshotPort cartSnapshots;
    private final GenerateOrderIdentityPort identities;
    private final GenerateOrderNumberPort orderNumbers;
    private final CurrentTimePort clock;

    public RegularPurchaseCheckoutService(PersistRegularPurchasePort persistence,
            LoadProductPurchaseQuotesPort productQuotes, CreateRegularStockHoldPort stockHolds,
            GenerateOrderIdentityPort identities, GenerateOrderNumberPort orderNumbers, CurrentTimePort clock) {
        this(persistence, productQuotes, stockHolds, null, identities, orderNumbers, clock);
    }

    public RegularPurchaseCheckoutService(PersistRegularPurchasePort persistence,
            LoadProductPurchaseQuotesPort productQuotes, CreateRegularStockHoldPort stockHolds,
            LoadCartCheckoutSnapshotPort cartSnapshots, GenerateOrderIdentityPort identities,
            GenerateOrderNumberPort orderNumbers, CurrentTimePort clock) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.productQuotes = Objects.requireNonNull(productQuotes, "productQuotes");
        this.stockHolds = Objects.requireNonNull(stockHolds, "stockHolds");
        this.cartSnapshots = cartSnapshots;
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

        return continueCheckout(intake, command.traceId(), command.traceparent(), command.tracestate());
    }

    @Override
    public RegularPurchaseCheckoutResult checkout(CartCheckoutCommand command) {
        Objects.requireNonNull(command, "command");
        if (cartSnapshots == null) {
            throw new RegularPurchaseDownstreamException(
                    RegularPurchaseDownstreamException.Failure.CART_SERVICE_UNAVAILABLE);
        }
        Instant receivedAt = clock.now();
        List<RegularPurchaseLine> submittedLines = command.lines().stream()
                .map(line -> new RegularPurchaseLine(line.variantId(), line.quantity(), line.expectedUnitPrice(),
                        line.currency(), line.itemVersion()))
                .toList();
        RegularPurchaseRequest candidate = RegularPurchaseRequest.receiveCart(identities.generate(),
                command.shopperId(), command.idempotencyKey(), identities.generate(), identities.generate(),
                command.cartVersion(), submittedLines, receivedAt);
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
            var snapshot = cartSnapshots.load(command.shopperId(), command.traceId());
            if (!matchesSnapshot(intake, snapshot)) {
                reject(intake, "CART_CHANGED");
                throw new RegularPurchaseBusinessException(
                        RegularPurchaseBusinessException.Reason.CART_CHANGED);
            }
            intake = persistence.update(intake.snapshotValidated(snapshot.cartId(), snapshot.cartVersion(),
                    clock.now()));
        }
        return continueCheckout(intake, command.traceId(), command.traceparent(), command.tracestate());
    }

    private RegularPurchaseCheckoutResult continueCheckout(RegularPurchaseRequest intake, String traceId,
            String traceparent, String tracestate) {
        if (intake.state() == RegularPurchaseRequestState.RECEIVED
                || intake.state() == RegularPurchaseRequestState.SNAPSHOT_VALIDATED) {
            List<ProductPurchaseQuote> quotes = productQuotes.loadQuotes(
                    intake.lines().stream().map(RegularPurchaseLine::variantId).toList(), traceId);
            validateQuotes(intake, quotes);
            intake = persistence.update(intake.productValidated(clock.now()));
        }
        if (intake.state() == RegularPurchaseRequestState.PRODUCT_VALIDATED) {
            try {
                RegularStockHold hold = stockHolds.create(new RegularStockHoldCommand(intake.proposedHoldId(),
                        intake.id(), intake.proposedOrderId(), intake.shopperId(), clock.now(), intake.lines().stream()
                                .map(line -> new RegularStockHoldCommand.RegularStockHoldLine(
                                        line.variantId(), line.quantity()))
                                .toList()), traceId);
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
                intake.proposedOrderId()), intake.id(), intake.source(), intake.proposedHoldId(),
                intake.shopperId(), intake.currency(), intake.lines().stream().map(line -> OrderLine.create(
                        identities.generate(), line.variantId(), line.quantity(), line.expectedUnitPrice())).toList(),
                intake.cartId(), intake.cartVersion(), acceptedAt, intake.holdExpiresAt());
        PurchaseSaga saga = PurchaseSaga.startRegular(order.id(), intake.id(), intake.proposedHoldId(),
                intake.holdExpiresAt(), acceptedAt);
        RegularPurchaseRequest accepted = intake.accept(order.id(), acceptedAt);
        persistence.accept(new RegularPurchaseAcceptance(accepted, order, saga, identities.generate(),
                identities.generate(), intake.id(), intake.id(), acceptedAt, traceparent, tracestate));
        return result(accepted, false);
    }

    private boolean matchesSnapshot(RegularPurchaseRequest intake,
            com.philia.flashsale.order.regularpurchase.application.model.CartCheckoutSnapshot snapshot) {
        if (snapshot == null || !intake.shopperId().equals(snapshot.ownerId())
                || !Objects.equals(intake.submittedCartVersion(), snapshot.cartVersion())
                || snapshot.items().size() != intake.lines().size()) {
            return false;
        }
        return intake.lines().stream().allMatch(line -> snapshot.items().stream().anyMatch(item ->
                line.variantId().equals(item.variantId()) && line.quantity() == item.quantity()
                        && Objects.equals(line.cartItemVersion(), item.itemVersion())));
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
            case "CART_CHANGED" -> new RegularPurchaseBusinessException(
                    RegularPurchaseBusinessException.Reason.CART_CHANGED);
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
