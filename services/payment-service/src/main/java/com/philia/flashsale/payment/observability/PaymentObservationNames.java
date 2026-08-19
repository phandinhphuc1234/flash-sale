package com.philia.flashsale.payment.observability;

/**
 * Stable, low-cardinality observation names for the Payment bounded context.
 *
 * <p>Names are intentionally owned by the service instead of being assembled from provider
 * values, payment IDs, URLs, or exception messages. This keeps dashboards and alerts stable while
 * preventing business or secret data from becoming telemetry dimensions.
 */
public final class PaymentObservationNames {
    public static final String HTTP_REQUEST = "payment.http.request";
    public static final String COMMAND_ACCEPTANCE = "payment.command.acceptance";
    public static final String CHECKOUT_CREATE = "payment.checkout.create";
    public static final String CHECKOUT_RETRIEVE = "payment.checkout.retrieve";
    public static final String CHECKOUT_EXPIRE = "payment.checkout.expire";
    public static final String WEBHOOK_VERIFY = "payment.webhook.verify";
    public static final String WEBHOOK_PROCESS = "payment.webhook.process";
    public static final String OUTBOX_PUBLISH = "payment.outbox.publish";
    public static final String RECOVERY_RECONCILE = "payment.recovery.reconcile";

    private PaymentObservationNames() {
    }
}
