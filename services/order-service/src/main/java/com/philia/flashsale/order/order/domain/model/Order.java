package com.philia.flashsale.order.order.domain.model;

import com.philia.flashsale.order.order.domain.exception.InvalidOrderException;
import com.philia.flashsale.order.order.domain.valueobject.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Order aggregate containing the immutable accepted-purchase snapshot. */
public final class Order {

    private final UUID id;
    private final String orderNumber;
    private final UUID purchaseRequestId;
    private final UUID reservationId;
    private final UUID campaignId;
    private final UUID userId;
    private final OrderStatus status;
    private final String currency;
    private final Money subtotal;
    private final Money total;
    private final Instant acceptedAt;
    private final Instant reservationExpiresAt;
    private final OrderLine line;

    private Order(UUID id, String orderNumber, UUID purchaseRequestId, UUID reservationId, UUID campaignId,
            UUID userId, OrderStatus status, String currency, Money subtotal, Money total,
            Instant acceptedAt, Instant reservationExpiresAt, OrderLine line) {
        this.id = require(id, "id");
        this.orderNumber = requireOrderNumber(orderNumber);
        this.purchaseRequestId = require(purchaseRequestId, "purchaseRequestId");
        this.reservationId = require(reservationId, "reservationId");
        this.campaignId = require(campaignId, "campaignId");
        this.userId = require(userId, "userId");
        this.status = Objects.requireNonNull(status, "status");
        this.currency = requireCurrency(currency);
        this.subtotal = Objects.requireNonNull(subtotal, "subtotal");
        this.total = Objects.requireNonNull(total, "total");
        this.acceptedAt = Objects.requireNonNull(acceptedAt, "acceptedAt");
        this.reservationExpiresAt = Objects.requireNonNull(reservationExpiresAt, "reservationExpiresAt");
        this.line = Objects.requireNonNull(line, "line");
        if (status != OrderStatus.PENDING_PAYMENT && status != OrderStatus.CONFIRMED
                && status != OrderStatus.CANCELLED && status != OrderStatus.EXPIRED) {
            throw new InvalidOrderException("unsupported Order status");
        }
        if (!acceptedAt.isBefore(reservationExpiresAt)) {
            throw new InvalidOrderException("reservation expiry must be after acceptance");
        }
        if (!subtotal.equals(line.lineAmount()) || !total.equals(line.lineAmount()) || !total.equals(subtotal)) {
            throw new InvalidOrderException("Order totals must equal the single line amount");
        }
    }

    /** Creates an Order from a fully formed immutable line snapshot. */
    public static Order create(UUID id, String orderNumber, UUID purchaseRequestId, UUID reservationId,
            UUID campaignId, UUID userId, String currency, OrderLine line,
            Instant acceptedAt, Instant reservationExpiresAt) {
        Money amount = line.lineAmount();
        return new Order(id, orderNumber, purchaseRequestId, reservationId, campaignId, userId,
                OrderStatus.PENDING_PAYMENT, currency, amount, amount, acceptedAt, reservationExpiresAt, line);
    }

    /** Returns the terminal paid state while preserving the accepted commercial snapshot. */
    public Order confirm() {
        if (status == OrderStatus.CONFIRMED) {
            return this;
        }
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new InvalidOrderException("Order is not awaiting payment confirmation");
        }
        return new Order(id, orderNumber, purchaseRequestId, reservationId, campaignId, userId,
                OrderStatus.CONFIRMED, currency, subtotal, total, acceptedAt, reservationExpiresAt, line);
    }

    /** Returns the unpaid terminal state after the reservation participant released the hold. */
    public Order terminalize(OrderStatus terminalStatus) {
        if (terminalStatus != OrderStatus.CANCELLED && terminalStatus != OrderStatus.EXPIRED) {
            throw new InvalidOrderException("unsupported unpaid terminal status");
        }
        if (status == terminalStatus) return this;
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new InvalidOrderException("Order is not awaiting payment release");
        }
        return new Order(id, orderNumber, purchaseRequestId, reservationId, campaignId, userId,
                terminalStatus, currency, subtotal, total, acceptedAt, reservationExpiresAt, line);
    }

    private static <T> T require(T value, String name) {
        return Objects.requireNonNull(value, name);
    }

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
    public UUID reservationId() { return reservationId; }
    public UUID campaignId() { return campaignId; }
    public UUID userId() { return userId; }
    public OrderStatus status() { return status; }
    public String currency() { return currency; }
    public Money subtotal() { return subtotal; }
    public Money total() { return total; }
    public Instant acceptedAt() { return acceptedAt; }
    public Instant reservationExpiresAt() { return reservationExpiresAt; }
    public OrderLine line() { return line; }
}
