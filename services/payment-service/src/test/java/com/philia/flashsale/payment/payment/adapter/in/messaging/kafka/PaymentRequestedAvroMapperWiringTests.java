package com.philia.flashsale.payment.payment.adapter.in.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.philia.flashsale.payment.configuration.PaymentKafkaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PaymentRequestedAvroMapperWiringTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PaymentRequestedAvroMapper.class)
            .withBean(PaymentKafkaProperties.class, () -> {
                PaymentKafkaProperties properties = mock(PaymentKafkaProperties.class);
                when(properties.commandTopic()).thenReturn("flashsale.payment.commands.v1");
                return properties;
            })
            .withPropertyValues("payment.kafka.consumer-enabled=true");

    @Test
    void wiresTheKafkaMapperThroughItsRuntimeConstructor() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PaymentRequestedAvroMapper.class);
        });
    }
}
