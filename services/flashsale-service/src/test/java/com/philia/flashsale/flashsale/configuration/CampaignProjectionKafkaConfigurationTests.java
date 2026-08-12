package com.philia.flashsale.flashsale.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;

class CampaignProjectionKafkaConfigurationTests {

    @Test
    void retryPolicyAllowsInitialDeliveryThen250And500MillisecondRetries() {
        var backOff = new CampaignProjectionKafkaConfiguration.CampaignProjectionBackOff(
                3, List.of(Duration.ofMillis(250), Duration.ofMillis(500)));
        var execution = backOff.start();

        assertThat(execution.nextBackOff()).isEqualTo(250);
        assertThat(execution.nextBackOff()).isEqualTo(500);
        assertThat(execution.nextBackOff()).isEqualTo(-1);
    }

    @Test
    void listenerFactoryUsesManualAcknowledgementAndObservation() {
        var configuration = new CampaignProjectionKafkaConfiguration();
        @SuppressWarnings("unchecked")
        ConsumerFactory<String, SpecificRecord> consumerFactory =
                org.mockito.Mockito.mock(ConsumerFactory.class);
        CampaignProjectionProperties properties = new CampaignProjectionProperties(
                "campaign.lifecycle.v1", "flashsale-campaign-projection-v1", Duration.ofSeconds(5),
                3, List.of(Duration.ofMillis(250), Duration.ofMillis(500)),
                URI.create("http://campaign-service:8080"));

        var factory = configuration.campaignProjectionKafkaListenerContainerFactory(
                consumerFactory, properties);

        assertThat(factory.getContainerProperties().getAckMode()).isEqualTo(AckMode.MANUAL);
        assertThat(factory.getContainerProperties().isObservationEnabled()).isTrue();
        assertThat(factory.getContainerProperties().isDeliveryAttemptHeader()).isTrue();
    }
}
