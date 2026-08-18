package com.philia.flashsale.payment.outbox.application.port;

import com.philia.flashsale.payment.outbox.application.model.PaymentOutboxEvent;

/** Publishes one immutable outbox snapshot to the approved Kafka result topic. */
public interface PublishPaymentOutboxPort {

    void publish(PaymentOutboxEvent event);
}
