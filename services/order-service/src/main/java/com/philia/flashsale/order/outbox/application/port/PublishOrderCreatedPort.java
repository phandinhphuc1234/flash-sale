package com.philia.flashsale.order.outbox.application.port;

import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;

/** Publishes one immutable Order-created fact outside the database transaction. */
public interface PublishOrderCreatedPort extends PublishOrderEventPort { }
