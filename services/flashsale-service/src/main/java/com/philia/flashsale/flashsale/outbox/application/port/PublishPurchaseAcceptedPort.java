package com.philia.flashsale.flashsale.outbox.application.port;

import com.philia.flashsale.flashsale.outbox.application.model.OutboxEvent;

/** Publishes one immutable PurchaseAccepted publication intent outside the claim transaction. */
public interface PublishPurchaseAcceptedPort extends PublishOutboxEventPort { }
