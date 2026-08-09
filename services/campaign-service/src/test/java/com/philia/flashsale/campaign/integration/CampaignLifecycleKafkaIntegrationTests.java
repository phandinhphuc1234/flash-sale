package com.philia.flashsale.campaign.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignActivatedDataV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignActivatedV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledDataV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledItemV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledV1;
import com.philia.flashsale.campaign.outbox.adapter.out.messaging.kafka.W3CTraceContextHeaders;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Live broker/Registry checks. Enable explicitly with
 * {@code -Dcampaign.kafka.integration=true}; the default unit build never needs external infra.
 */
@EnabledIfSystemProperty(named = "campaign.kafka.integration", matches = "true")
class CampaignLifecycleKafkaIntegrationTests {

    private static final String TOPIC = "campaign.lifecycle.v1";
    private static final Instant EVENT_TIME = Instant.parse("2030-08-01T10:00:00Z");

    @Test
    void publishesTypedEventsWithStableKeysOrderDuplicateIdentityAndTraceHeader() throws Exception {
        UUID campaignA = UUID.randomUUID();
        UUID campaignB = UUID.randomUUID();
        UUID scheduledId = UUID.randomUUID();
        UUID activatedId = UUID.randomUUID();

        Properties producerProperties = producerProperties();
        try (KafkaProducer<String, SpecificRecord> producer = new KafkaProducer<>(producerProperties)) {
            producer.send(record(campaignA, scheduled(scheduledId, campaignA), scheduledId))
                    .get(10, TimeUnit.SECONDS);
            producer.send(record(campaignA, activated(activatedId, campaignA), activatedId))
                    .get(10, TimeUnit.SECONDS);
            UUID independentId = UUID.randomUUID();
            producer.send(record(campaignB, scheduled(independentId, campaignB), independentId))
                    .get(10, TimeUnit.SECONDS);
            // A redelivery keeps the original business identity; consumers deduplicate by eventId.
            producer.send(record(campaignA, scheduled(scheduledId, campaignA), scheduledId))
                    .get(10, TimeUnit.SECONDS);
        }

        List<ConsumerRecord<String, Object>> records = consume(campaignA, campaignB, scheduledId, activatedId);
        ConsumerRecord<String, Object> scheduledRecord = records.stream()
                .filter(record -> record.value() instanceof CampaignScheduledV1 event
                        && scheduledId.equals(event.getEventId()))
                .findFirst().orElseThrow();
        ConsumerRecord<String, Object> activatedRecord = records.stream()
                .filter(record -> record.value() instanceof CampaignActivatedV1 event
                        && activatedId.equals(event.getEventId()))
                .findFirst().orElseThrow();

        assertThat(scheduledRecord.key()).isEqualTo(campaignA.toString());
        assertThat(activatedRecord.key()).isEqualTo(campaignA.toString());
        assertThat(scheduledRecord.partition()).isEqualTo(activatedRecord.partition());
        assertThat(scheduledRecord.offset()).isLessThan(activatedRecord.offset());
        assertThat(records.stream().filter(record -> record.value() instanceof CampaignScheduledV1 event
                && scheduledId.equals(event.getEventId())).count()).isEqualTo(2);
        assertThat(new String(scheduledRecord.headers().lastHeader("traceparent").value()))
                .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
        assertThat(registryCompatibility()).contains("BACKWARD_TRANSITIVE");
    }

    private List<ConsumerRecord<String, Object>> consume(
            UUID campaignA, UUID campaignB, UUID scheduledId, UUID activatedId) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "campaign-b6-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        properties.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl());
        properties.put("specific.avro.reader", true);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        List<ConsumerRecord<String, Object>> result = new ArrayList<>();
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        try (KafkaConsumer<String, Object> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(TOPIC));
            while (System.nanoTime() < deadline && !containsAll(result, campaignA, campaignB, scheduledId, activatedId)) {
                consumer.poll(Duration.ofMillis(500)).forEach(result::add);
            }
        }
        return result;
    }

    private boolean containsAll(List<ConsumerRecord<String, Object>> records,
            UUID campaignA, UUID campaignB, UUID scheduledId, UUID activatedId) {
        return records.stream().anyMatch(record -> campaignA.toString().equals(record.key()))
                && records.stream().anyMatch(record -> campaignB.toString().equals(record.key()))
                && records.stream().anyMatch(record -> record.value() instanceof CampaignScheduledV1 event
                        && scheduledId.equals(event.getEventId()))
                && records.stream().anyMatch(record -> record.value() instanceof CampaignActivatedV1 event
                        && activatedId.equals(event.getEventId()));
    }

    private Properties producerProperties() {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        properties.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl());
        properties.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, false);
        properties.put(AbstractKafkaSchemaSerDeConfig.VALUE_SUBJECT_NAME_STRATEGY, 
                "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return properties;
    }

    private ProducerRecord<String, SpecificRecord> record(
            UUID campaignId, SpecificRecord value, UUID eventId) {
        ProducerRecord<String, SpecificRecord> record =
                new ProducerRecord<>(TOPIC, campaignId.toString(), value);
        record.headers().add("eventId", eventId.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        record.headers().add("traceparent", W3CTraceContextHeaders.traceparent(eventId.toString()));
        return record;
    }

    private String registryCompatibility() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(schemaRegistryUrl() + "/config"))
                .timeout(Duration.ofSeconds(5)).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private String bootstrapServers() {
        return System.getProperty("campaign.kafka.bootstrap", "localhost:29092");
    }

    private String schemaRegistryUrl() {
        return System.getProperty("campaign.schema-registry.url", "http://localhost:8081");
    }

    private CampaignScheduledV1 scheduled(UUID eventId, UUID campaignId) {
        return new CampaignScheduledV1(eventId, "CampaignScheduled", 1, "Campaign", campaignId, 1L,
                EVENT_TIME, new CampaignScheduledDataV1(campaignId, "B6", EVENT_TIME,
                        EVENT_TIME.plusSeconds(3600), new CampaignScheduledItemV1(
                                UUID.randomUUID(), UUID.randomUUID(), "SKU-B6", new java.math.BigDecimal("9.9900"),
                                "VND", 10L, 1L)));
    }

    private CampaignActivatedV1 activated(UUID eventId, UUID campaignId) {
        return new CampaignActivatedV1(eventId, "CampaignActivated", 1, "Campaign", campaignId, 2L,
                EVENT_TIME, new CampaignActivatedDataV1(campaignId, EVENT_TIME,
                        EVENT_TIME.plusSeconds(3600)));
    }
}
