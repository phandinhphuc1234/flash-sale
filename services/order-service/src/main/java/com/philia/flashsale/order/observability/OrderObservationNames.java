package com.philia.flashsale.order.observability;

/** Low-cardinality observation names owned by the Order runtime boundaries. */
public final class OrderObservationNames {
    public static final String INBOUND_PROCESSING = "order.inbound.processing";
    public static final String DURABLE_CREATION = "order.durable.creation";
    public static final String OWNER_QUERY = "order.owner.query";
    public static final String OUTBOX_PUBLICATION = "order.outbox.publication";
    public static final String REGULAR_INTAKE = "order.regular_purchase.intake";
    public static final String REGULAR_RECOVERY = "order.regular_purchase.recovery";

    private OrderObservationNames() {
    }
}
