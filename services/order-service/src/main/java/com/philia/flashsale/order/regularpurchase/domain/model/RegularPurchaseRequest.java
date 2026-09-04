package com.philia.flashsale.order.regularpurchase.domain.model;

import com.philia.flashsale.order.order.domain.model.PurchaseSource;
import com.philia.flashsale.order.regularpurchase.domain.exception.InvalidRegularPurchaseRequestException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Order-owned durable intent for one regular Buy Now or Cart checkout attempt.
 *
 * <p>The fingerprint is deliberately derived only from the canonical body supplied by the browser.
 * A Cart ID is an internal Cart-owned identity obtained later from the owner-bound snapshot and is
 * never part of the initial idempotency identity.</p>
 */
public final class RegularPurchaseRequest {
    public static final int MAX_CART_LINES = 20;
    public static final int MAX_LINE_QUANTITY = 10;
    public static final Duration INVENTORY_HOLD_TTL = Duration.ofMinutes(5);
    public static final Duration PAYMENT_SAFETY_MARGIN = Duration.ofSeconds(30);

    private final UUID id;
    private final UUID shopperId;
    private final String idempotencyKey;
    private final String requestFingerprint;
    private final PurchaseSource source;
    private final UUID proposedOrderId;
    private final UUID proposedHoldId;
    private final List<RegularPurchaseLine> lines;
    private final Long submittedCartVersion;
    private final UUID cartId;
    private final Long cartVersion;
    private final RegularPurchaseRequestState state;
    private final Instant holdExpiresAt;
    private final UUID orderId;
    private final String rejectionCode;
    private final Instant createdAt;
    private final Instant updatedAt;

    private RegularPurchaseRequest(UUID id, UUID shopperId, String idempotencyKey, String requestFingerprint,
            PurchaseSource source, UUID proposedOrderId, UUID proposedHoldId, List<RegularPurchaseLine> lines,
            Long submittedCartVersion, UUID cartId, Long cartVersion, RegularPurchaseRequestState state,
            Instant holdExpiresAt, UUID orderId, String rejectionCode, Instant createdAt, Instant updatedAt) {
        this.id = require(id, "id");
        this.shopperId = require(shopperId, "shopperId");
        this.idempotencyKey = requireIdempotencyKey(idempotencyKey);
        this.requestFingerprint = requireFingerprint(requestFingerprint);
        this.source = requireRegularSource(source);
        this.proposedOrderId = require(proposedOrderId, "proposedOrderId");
        this.proposedHoldId = require(proposedHoldId, "proposedHoldId");
        this.lines = canonicalLines(lines);
        this.submittedCartVersion = submittedCartVersion;
        this.cartId = cartId;
        this.cartVersion = cartVersion;
        this.state = require(state, "state");
        this.holdExpiresAt = holdExpiresAt;
        this.orderId = orderId;
        this.rejectionCode = rejectionCode;
        this.createdAt = require(createdAt, "createdAt");
        this.updatedAt = require(updatedAt, "updatedAt");
        validateInvariant();
    }

    /** Starts an idempotent Buy Now request with exactly one shopper-confirmed line. */
    public static RegularPurchaseRequest receiveBuyNow(UUID id, UUID shopperId, String idempotencyKey,
            UUID proposedOrderId, UUID proposedHoldId, RegularPurchaseLine line, Instant receivedAt) {
        List<RegularPurchaseLine> lines = List.of(require(line, "line"));
        return receive(id, shopperId, idempotencyKey, proposedOrderId, proposedHoldId, PurchaseSource.BUY_NOW,
                lines, null, receivedAt);
    }

    /** Starts a Cart request without accepting a browser-selected Cart identity. */
    public static RegularPurchaseRequest receiveCart(UUID id, UUID shopperId, String idempotencyKey,
            UUID proposedOrderId, UUID proposedHoldId, long submittedCartVersion, List<RegularPurchaseLine> lines,
            Instant receivedAt) {
        if (submittedCartVersion < 0) {
            throw new InvalidRegularPurchaseRequestException("submitted Cart version must be non-negative");
        }
        return receive(id, shopperId, idempotencyKey, proposedOrderId, proposedHoldId, PurchaseSource.CART, lines,
                submittedCartVersion, receivedAt);
    }

    /** Rehydrates a request from Order-owned persistence without exposing JPA to the domain. */
    public static RegularPurchaseRequest restore(UUID id, UUID shopperId, String idempotencyKey,
            String requestFingerprint, PurchaseSource source, UUID proposedOrderId, UUID proposedHoldId,
            List<RegularPurchaseLine> lines, Long submittedCartVersion, UUID cartId, Long cartVersion,
            RegularPurchaseRequestState state, Instant holdExpiresAt, UUID orderId, String rejectionCode,
            Instant createdAt, Instant updatedAt) {
        return new RegularPurchaseRequest(id, shopperId, idempotencyKey, requestFingerprint, source,
                proposedOrderId, proposedHoldId, lines, submittedCartVersion, cartId, cartVersion, state,
                holdExpiresAt, orderId, rejectionCode, createdAt, updatedAt);
    }

    /** Attaches the Cart-owned identity only after the internal owner-bound snapshot succeeds. */
    public RegularPurchaseRequest snapshotValidated(UUID snapshotCartId, long snapshotCartVersion,
            Instant transitionedAt) {
        requireState(RegularPurchaseRequestState.RECEIVED, "Cart snapshot validation");
        if (source != PurchaseSource.CART) {
            throw new InvalidRegularPurchaseRequestException("only Cart requests may validate a Cart snapshot");
        }
        if (snapshotCartVersion != submittedCartVersion) {
            throw new InvalidRegularPurchaseRequestException("Cart snapshot version differs from submitted version");
        }
        return copy(snapshotCartId, snapshotCartVersion, RegularPurchaseRequestState.SNAPSHOT_VALIDATED, null, null,
                null, transitionedAt);
    }

    /** Records that the exact request lines have passed Product-owned quote validation. */
    public RegularPurchaseRequest productValidated(Instant transitionedAt) {
        if (source == PurchaseSource.BUY_NOW) {
            requireState(RegularPurchaseRequestState.RECEIVED, "Product validation");
        } else {
            requireState(RegularPurchaseRequestState.SNAPSHOT_VALIDATED, "Product validation");
        }
        return copy(cartId, cartVersion, RegularPurchaseRequestState.PRODUCT_VALIDATED, null, null, null,
                transitionedAt);
    }

    /**
     * Records the Inventory-owned five-minute hold. Inventory determines the precise start with its
     * own clock; Order verifies that the returned expiry leaves the mandatory future payment window.
     */
    public RegularPurchaseRequest holdAcquired(Instant inventoryHoldExpiresAt, Instant transitionedAt) {
        requireState(RegularPurchaseRequestState.PRODUCT_VALIDATED, "regular stock hold acquisition");
        paymentDeadline(inventoryHoldExpiresAt, transitionedAt);
        return copy(cartId, cartVersion, RegularPurchaseRequestState.HOLD_ACQUIRED, inventoryHoldExpiresAt, null,
                null, transitionedAt);
    }

    /** Completes the only legal post-hold intake transition using the stable proposed Order identity. */
    public RegularPurchaseRequest accept(UUID acceptedOrderId, Instant transitionedAt) {
        requireState(RegularPurchaseRequestState.HOLD_ACQUIRED, "acceptance");
        if (!proposedOrderId.equals(acceptedOrderId)) {
            throw new InvalidRegularPurchaseRequestException("accepted Order identity differs from proposed Order");
        }
        return copy(cartId, cartVersion, RegularPurchaseRequestState.ACCEPTED, holdExpiresAt, acceptedOrderId, null,
                transitionedAt);
    }

    /** Rejects only before a durable stock hold exists, so a hold cannot be silently orphaned. */
    public RegularPurchaseRequest reject(String stableRejectionCode, Instant transitionedAt) {
        if (!state.permitsBusinessRejection()) {
            throw new InvalidRegularPurchaseRequestException("a stock-held request cannot become a business rejection");
        }
        return copy(cartId, cartVersion, RegularPurchaseRequestState.REJECTED, null, null,
                requireRejectionCode(stableRejectionCode), transitionedAt);
    }

    /** Evaluates the documented shopper/key/fingerprint idempotency boundary. */
    public IdempotencyMatch idempotencyMatch(UUID requestingShopperId, String suppliedIdempotencyKey,
            String suppliedFingerprint) {
        if (!shopperId.equals(requestingShopperId) || !idempotencyKey.equals(suppliedIdempotencyKey)) {
            return IdempotencyMatch.NOT_MATCHED;
        }
        return requestFingerprint.equals(suppliedFingerprint) ? IdempotencyMatch.REPLAY : IdempotencyMatch.CONFLICT;
    }

    /** Calculates the exact expiry-minus-thirty-seconds Payment deadline required by this feature. */
    public static Instant paymentDeadline(Instant holdExpiresAt, Instant requestedAt) {
        Objects.requireNonNull(holdExpiresAt, "holdExpiresAt");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Instant deadline = holdExpiresAt.minus(PAYMENT_SAFETY_MARGIN);
        if (!deadline.isAfter(requestedAt)) {
            throw new InvalidRegularPurchaseRequestException(
                    "Inventory hold expiry does not leave a future payment window");
        }
        return deadline;
    }

    private static RegularPurchaseRequest receive(UUID id, UUID shopperId, String idempotencyKey,
            UUID proposedOrderId, UUID proposedHoldId, PurchaseSource source, List<RegularPurchaseLine> lines,
            Long submittedCartVersion, Instant receivedAt) {
        List<RegularPurchaseLine> canonicalLines = canonicalLines(lines);
        String fingerprint = fingerprint(source, submittedCartVersion, canonicalLines);
        return new RegularPurchaseRequest(id, shopperId, idempotencyKey, fingerprint, source, proposedOrderId,
                proposedHoldId, canonicalLines, submittedCartVersion, null, null, RegularPurchaseRequestState.RECEIVED,
                null, null, null, receivedAt, receivedAt);
    }

    private RegularPurchaseRequest copy(UUID nextCartId, Long nextCartVersion, RegularPurchaseRequestState nextState,
            Instant nextHoldExpiresAt, UUID nextOrderId, String nextRejectionCode, Instant transitionedAt) {
        requireTransitionTime(transitionedAt);
        return new RegularPurchaseRequest(id, shopperId, idempotencyKey, requestFingerprint, source, proposedOrderId,
                proposedHoldId, lines, submittedCartVersion, nextCartId, nextCartVersion, nextState,
                nextHoldExpiresAt, nextOrderId, nextRejectionCode, createdAt, transitionedAt);
    }

    private void validateInvariant() {
        if (updatedAt.isBefore(createdAt)) {
            throw new InvalidRegularPurchaseRequestException("request update time must not precede creation");
        }
        validateSourceShape();
        if (!requestFingerprint.equals(fingerprint(source, submittedCartVersion, lines))) {
            throw new InvalidRegularPurchaseRequestException(
                    "request fingerprint must match the canonical submitted body");
        }
        validateStateShape();
    }

    private void validateSourceShape() {
        boolean hasCartIdentity = cartId != null || cartVersion != null;
        if (source == PurchaseSource.BUY_NOW) {
            if (lines.size() != 1 || submittedCartVersion != null || hasCartIdentity
                    || lines.stream().anyMatch(line -> line.cartItemVersion() != null)) {
                throw new InvalidRegularPurchaseRequestException("Buy Now request must contain one non-Cart line");
            }
            return;
        }
        if (submittedCartVersion == null || submittedCartVersion < 0
                || lines.stream().anyMatch(line -> line.cartItemVersion() == null)) {
            throw new InvalidRegularPurchaseRequestException(
                    "Cart request requires a submitted Cart revision and item revisions");
        }
        if ((cartId == null) != (cartVersion == null) || (cartVersion != null && cartVersion < 0)) {
            throw new InvalidRegularPurchaseRequestException("Cart identity and revision must be present together");
        }
    }

    private void validateStateShape() {
        boolean hasCartIdentity = cartId != null;
        if (source == PurchaseSource.CART) {
            if (state == RegularPurchaseRequestState.RECEIVED && hasCartIdentity) {
                throw new InvalidRegularPurchaseRequestException("received Cart request must not have a Cart identity");
            }
            if ((state == RegularPurchaseRequestState.SNAPSHOT_VALIDATED
                    || state == RegularPurchaseRequestState.PRODUCT_VALIDATED
                    || state == RegularPurchaseRequestState.HOLD_ACQUIRED
                    || state == RegularPurchaseRequestState.ACCEPTED) && !hasCartIdentity) {
                throw new InvalidRegularPurchaseRequestException("snapshot-bound Cart request requires Cart identity");
            }
        }
        boolean requiresHold = state == RegularPurchaseRequestState.HOLD_ACQUIRED
                || state == RegularPurchaseRequestState.ACCEPTED;
        if (requiresHold != (holdExpiresAt != null)) {
            throw new InvalidRegularPurchaseRequestException("hold expiry must exist only after stock hold acquisition");
        }
        if (state == RegularPurchaseRequestState.ACCEPTED) {
            if (orderId == null || !orderId.equals(proposedOrderId)) {
                throw new InvalidRegularPurchaseRequestException("accepted request requires the proposed Order identity");
            }
        } else if (orderId != null) {
            throw new InvalidRegularPurchaseRequestException("only an accepted request may have an Order identity");
        }
        if (state == RegularPurchaseRequestState.REJECTED) {
            requireRejectionCode(rejectionCode);
        } else if (rejectionCode != null) {
            throw new InvalidRegularPurchaseRequestException("only a rejected request may have a rejection code");
        }
    }

    private static List<RegularPurchaseLine> canonicalLines(List<RegularPurchaseLine> requestedLines) {
        if (requestedLines == null || requestedLines.isEmpty() || requestedLines.size() > MAX_CART_LINES) {
            throw new InvalidRegularPurchaseRequestException("regular purchase must contain between one and twenty lines");
        }
        List<RegularPurchaseLine> canonical = requestedLines.stream()
                .map(line -> require(line, "line"))
                .sorted(Comparator.comparing(RegularPurchaseLine::variantId))
                .toList();
        if (canonical.stream().map(RegularPurchaseLine::variantId).distinct().count() != canonical.size()) {
            throw new InvalidRegularPurchaseRequestException("regular purchase line variants must be distinct");
        }
        String currency = canonical.getFirst().currency();
        if (canonical.stream().anyMatch(line -> !currency.equals(line.currency()))) {
            throw new InvalidRegularPurchaseRequestException("regular purchase lines must use one currency");
        }
        return canonical;
    }

    private static String fingerprint(PurchaseSource source, Long submittedCartVersion,
            List<RegularPurchaseLine> canonicalLines) {
        PurchaseSource regularSource = requireRegularSource(source);
        StringBuilder canonical = new StringBuilder("source=").append(regularSource.name()).append('\n');
        if (regularSource == PurchaseSource.CART) {
            canonical.append("submittedCartVersion=").append(submittedCartVersion).append('\n');
        }
        for (RegularPurchaseLine line : canonicalLines) {
            canonical.append(line.variantId()).append('|')
                    .append(line.quantity()).append('|')
                    .append(line.expectedUnitPrice().amount().toPlainString()).append('|')
                    .append(line.currency()).append('|')
                    .append(line.cartItemVersion() == null ? "" : line.cartItemVersion()).append('\n');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private void requireState(RegularPurchaseRequestState expected, String operation) {
        if (state != expected) {
            throw new InvalidRegularPurchaseRequestException(operation + " is not valid from " + state);
        }
    }

    private void requireTransitionTime(Instant transitionedAt) {
        Objects.requireNonNull(transitionedAt, "transitionedAt");
        if (transitionedAt.isBefore(updatedAt)) {
            throw new InvalidRegularPurchaseRequestException("request transition time must not move backwards");
        }
    }

    private static PurchaseSource requireRegularSource(PurchaseSource source) {
        if (source != PurchaseSource.BUY_NOW && source != PurchaseSource.CART) {
            throw new InvalidRegularPurchaseRequestException("regular request source must be Buy Now or Cart");
        }
        return source;
    }

    private static String requireIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new InvalidRegularPurchaseRequestException("idempotency key must contain one to 128 characters");
        }
        return value;
    }

    private static String requireFingerprint(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new InvalidRegularPurchaseRequestException("request fingerprint must be lowercase SHA-256");
        }
        return value;
    }

    private static String requireRejectionCode(String value) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new InvalidRegularPurchaseRequestException("rejection code must be a bounded stable code");
        }
        return value;
    }

    private static <T> T require(T value, String name) {
        return Objects.requireNonNull(value, name);
    }

    public UUID id() { return id; }
    public UUID shopperId() { return shopperId; }
    public String idempotencyKey() { return idempotencyKey; }
    public String requestFingerprint() { return requestFingerprint; }
    public PurchaseSource source() { return source; }
    public UUID proposedOrderId() { return proposedOrderId; }
    public UUID proposedHoldId() { return proposedHoldId; }
    public List<RegularPurchaseLine> lines() { return lines; }
    public String currency() { return lines.getFirst().currency(); }
    public Long submittedCartVersion() { return submittedCartVersion; }
    public UUID cartId() { return cartId; }
    public Long cartVersion() { return cartVersion; }
    public RegularPurchaseRequestState state() { return state; }
    public Instant holdExpiresAt() { return holdExpiresAt; }
    public UUID orderId() { return orderId; }
    public String rejectionCode() { return rejectionCode; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
