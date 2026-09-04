package com.philia.flashsale.order.order.domain.model;

import com.philia.flashsale.order.order.domain.exception.InvalidOrderException;
import com.philia.flashsale.order.order.domain.valueobject.Money;
import com.philia.flashsale.order.purchasesaga.domain.model.StockParticipantType;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Order-owned immutable accepted commercial snapshot for Flash Sale and regular checkout. */
public final class Order {
    private final UUID id;
    private final String orderNumber;
    private final UUID purchaseRequestId;
    private final PurchaseSource purchaseSource;
    private final StockParticipantType stockParticipantType;
    private final UUID stockReferenceId;
    private final UUID reservationId;
    private final UUID campaignId;
    private final UUID cartId;
    private final Long cartVersion;
    private final UUID userId;
    private final OrderStatus status;
    private final String currency;
    private final Money subtotal;
    private final Money total;
    private final Instant acceptedAt;
    private final Instant reservationExpiresAt;
    private final List<OrderLine> lines;

    private Order(UUID id, String orderNumber, UUID purchaseRequestId, PurchaseSource purchaseSource,
            StockParticipantType stockParticipantType, UUID stockReferenceId, UUID reservationId, UUID campaignId,
            UUID cartId, Long cartVersion, UUID userId, OrderStatus status, String currency, Money subtotal,
            Money total, Instant acceptedAt, Instant reservationExpiresAt, List<OrderLine> lines) {
        this.id = require(id, "id");
        this.orderNumber = requireOrderNumber(orderNumber);
        this.purchaseRequestId = require(purchaseRequestId, "purchaseRequestId");
        this.purchaseSource = require(purchaseSource, "purchaseSource");
        this.stockParticipantType = require(stockParticipantType, "stockParticipantType");
        this.stockReferenceId = require(stockReferenceId, "stockReferenceId");
        this.reservationId = reservationId;
        this.campaignId = campaignId;
        this.cartId = cartId;
        this.cartVersion = cartVersion;
        this.userId = require(userId, "userId");
        this.status = Objects.requireNonNull(status, "status");
        this.currency = requireCurrency(currency);
        this.subtotal = Objects.requireNonNull(subtotal, "subtotal");
        this.total = Objects.requireNonNull(total, "total");
        this.acceptedAt = Objects.requireNonNull(acceptedAt, "acceptedAt");
        this.reservationExpiresAt = Objects.requireNonNull(reservationExpiresAt, "reservationExpiresAt");
        this.lines = requireLines(lines);
        validateCrossFields();
        if (status != OrderStatus.PENDING_PAYMENT && status != OrderStatus.CONFIRMED
                && status != OrderStatus.CANCELLED && status != OrderStatus.EXPIRED) {
            throw new InvalidOrderException("unsupported Order status");
        }
        if (!acceptedAt.isBefore(reservationExpiresAt)) {
            throw new InvalidOrderException("reservation expiry must be after acceptance");
        }
        Money calculated = this.lines.stream().map(OrderLine::lineAmount).reduce(Money::add).orElseThrow();
        if (!subtotal.equals(calculated) || !total.equals(calculated) || !total.equals(subtotal)) {
            throw new InvalidOrderException("Order totals must equal the exact sum of lines");
        }
    }

    /** Existing Flash Sale factory retained verbatim at its public boundary. */
    public static Order create(UUID id, String orderNumber, UUID purchaseRequestId, UUID reservationId,
            UUID campaignId, UUID userId, String currency, OrderLine line, Instant acceptedAt,
            Instant reservationExpiresAt) {
        Money amount = line.lineAmount();
        return new Order(id, orderNumber, purchaseRequestId, PurchaseSource.FLASH_SALE,
                StockParticipantType.FLASH_SALE_RESERVATION, reservationId, reservationId, campaignId, null, null,
                userId, OrderStatus.PENDING_PAYMENT, currency, amount, amount, acceptedAt, reservationExpiresAt,
                List.of(line));
    }

    /** Creates a regular Buy Now or Cart Order after Product quote and Inventory hold succeed. */
    public static Order regular(UUID id, String orderNumber, UUID purchaseRequestId, PurchaseSource source,
            UUID regularHoldId, UUID userId, String currency, List<OrderLine> lines, UUID cartId, Long cartVersion,
            Instant acceptedAt, Instant holdExpiresAt) {
        Money amount = requireLines(lines).stream().map(OrderLine::lineAmount).reduce(Money::add).orElseThrow();
        return new Order(id, orderNumber, purchaseRequestId, source, StockParticipantType.REGULAR_STOCK_HOLD,
                regularHoldId, null, null, cartId, cartVersion, userId, OrderStatus.PENDING_PAYMENT, currency,
                amount, amount, acceptedAt, holdExpiresAt, lines);
    }

    /** Returns the terminal paid state while preserving the accepted commercial snapshot. */
    public Order confirm() {
        if (status == OrderStatus.CONFIRMED) return this;
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new InvalidOrderException("Order is not awaiting payment confirmation");
        }
        return copyWithStatus(OrderStatus.CONFIRMED);
    }

    /** Returns an unpaid terminal state after the selected stock participant released its hold. */
    public Order terminalize(OrderStatus terminalStatus) {
        if (terminalStatus != OrderStatus.CANCELLED && terminalStatus != OrderStatus.EXPIRED) {
            throw new InvalidOrderException("unsupported unpaid terminal status");
        }
        if (status == terminalStatus) return this;
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new InvalidOrderException("Order is not awaiting payment release");
        }
        return copyWithStatus(terminalStatus);
    }

    /** Legacy one-line accessor is only available for existing Flash Sale callers. */
    public OrderLine line() {
        if (lines.size() != 1) {
            throw new InvalidOrderException("multi-line Order has no singular line");
        }
        return lines.getFirst();
    }

    private Order copyWithStatus(OrderStatus nextStatus) {
        return new Order(id, orderNumber, purchaseRequestId, purchaseSource, stockParticipantType,
                stockReferenceId, reservationId, campaignId, cartId, cartVersion, userId, nextStatus, currency,
                subtotal, total, acceptedAt, reservationExpiresAt, lines);
    }

    private void validateCrossFields() {
        if (purchaseSource == PurchaseSource.FLASH_SALE) {
            if (stockParticipantType != StockParticipantType.FLASH_SALE_RESERVATION || reservationId == null
                    || campaignId == null || !reservationId.equals(stockReferenceId) || cartId != null
                    || cartVersion != null) {
                throw new InvalidOrderException("Flash Sale Order participant identity is invalid");
            }
            return;
        }
        if (stockParticipantType != StockParticipantType.REGULAR_STOCK_HOLD || reservationId != null
                || campaignId != null) {
            throw new InvalidOrderException("regular Order participant identity is invalid");
        }
        if (purchaseSource == PurchaseSource.BUY_NOW && (cartId != null || cartVersion != null || lines.size() != 1)) {
            throw new InvalidOrderException("Buy Now Order must have exactly one line and no Cart reference");
        }
        if (purchaseSource == PurchaseSource.CART && (cartId == null || cartVersion == null || cartVersion < 0)) {
            throw new InvalidOrderException("Cart Order requires a non-negative Cart revision");
        }
    }

    private static List<OrderLine> requireLines(List<OrderLine> lines) {
        if (lines == null || lines.isEmpty() || lines.size() > 20) {
            throw new InvalidOrderException("Order must contain between one and twenty lines");
        }
        List<OrderLine> ordered = lines.stream().sorted(Comparator.comparing(OrderLine::variantId)).toList();
        if (ordered.stream().map(OrderLine::variantId).distinct().count() != ordered.size()) {
            throw new InvalidOrderException("Order line variants must be distinct");
        }
        return ordered;
    }

    private static <T> T require(T value, String name) { return Objects.requireNonNull(value, name); }

    private static String requireOrderNumber(String value) {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw new InvalidOrderException("order number must be nonblank and at most 64 characters");
        }
        return value;
    }

    private static String requireCurrency(String value) {
        if (value == null || !value.matches("[A-Z]{3}")) {
            throw new InvalidOrderException("currency must be three uppercase ASCII letters");
        }
        return value;
    }

    public UUID id() { return id; }
    public String orderNumber() { return orderNumber; }
    public UUID purchaseRequestId() { return purchaseRequestId; }
    public PurchaseSource purchaseSource() { return purchaseSource; }
    public StockParticipantType stockParticipantType() { return stockParticipantType; }
    public UUID stockReferenceId() { return stockReferenceId; }
    public UUID reservationId() { return reservationId; }
    public UUID campaignId() { return campaignId; }
    public UUID cartId() { return cartId; }
    public Long cartVersion() { return cartVersion; }
    public UUID userId() { return userId; }
    public OrderStatus status() { return status; }
    public String currency() { return currency; }
    public Money subtotal() { return subtotal; }
    public Money total() { return total; }
    public Instant acceptedAt() { return acceptedAt; }
    /** Generic participant-hold expiry; the legacy reservation name remains for Flash Sale callers. */
    public Instant stockParticipantExpiresAt() { return reservationExpiresAt; }
    public Instant reservationExpiresAt() { return reservationExpiresAt; }
    public List<OrderLine> lines() { return lines; }
}
