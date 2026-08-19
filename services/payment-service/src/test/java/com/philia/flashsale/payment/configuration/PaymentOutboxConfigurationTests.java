package com.philia.flashsale.payment.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;

/** Producer configuration proof for idempotent Avro publication and explicit Schema Registry use. */
class PaymentOutboxConfigurationTests {

    @Test
    void enforcesIdempotentProducerAndDisablesSchemaAutoRegistration() {
        var properties = new PaymentKafkaProducerConfiguration()
                .paymentProducerProperties(new KafkaProperties());

        assertThat(properties).containsEntry("acks", "all");
        assertThat(properties).containsEntry("enable.idempotence", true);
        assertThat(properties).containsEntry("auto.register.schemas", false);
        assertThat(properties.get("value.subject.name.strategy"))
                .isEqualTo("io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");
    }
}
