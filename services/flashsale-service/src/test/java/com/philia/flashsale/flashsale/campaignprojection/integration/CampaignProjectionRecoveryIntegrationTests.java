package com.philia.flashsale.flashsale.campaignprojection.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignActivatedDataV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignActivatedV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledDataV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledItemV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledV1;
import com.philia.flashsale.flashsale.campaignprojection.adapter.in.messaging.kafka.CampaignLifecycleAvroMapper;
import com.philia.flashsale.flashsale.campaignprojection.adapter.in.messaging.kafka.CampaignLifecycleKafkaConsumer;
import com.philia.flashsale.flashsale.campaignprojection.adapter.out.redis.CampaignProjectionRedisAdapter;
import com.philia.flashsale.flashsale.campaignprojection.adapter.out.redis.CampaignProjectionRedisKeys;
import com.philia.flashsale.flashsale.campaignprojection.adapter.out.redis.CampaignProjectionRecoveryRedisAdapter;
import com.philia.flashsale.flashsale.campaignprojection.application.port.out.LoadCampaignSnapshotPort;
import com.philia.flashsale.flashsale.campaignprojection.application.usecase.CampaignProjectionRecoveryService;
import com.philia.flashsale.flashsale.campaignprojection.application.usecase.CampaignProjectionService;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignItemProjection;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignProjectionState;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignSaleProjection;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CampaignProjectionRecoveryIntegrationTests {
    private static final Instant START = Instant.parse("2030-08-01T10:00:00Z");
    private static final Instant END = START.plusSeconds(3600);
    private static final String TOPIC = "campaign.lifecycle.v1";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;
    private CampaignProjectionRedisAdapter store;
    private CampaignProjectionRecoveryRedisAdapter recoveryQueue;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        store = new CampaignProjectionRedisAdapter(redis);
        recoveryQueue = new CampaignProjectionRecoveryRedisAdapter(redis);
        try (var connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void scheduledKafkaFactCreatesTheRedisProjectionWithExactSnapshot() {
        UUID campaignId = UUID.randomUUID();
        CampaignProjectionService projectionService = new CampaignProjectionService(store, recoveryQueue);
        CampaignLifecycleKafkaConsumer consumer = new CampaignLifecycleKafkaConsumer(
                new CampaignLifecycleAvroMapper(), projectionService, projectionService);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);

        consumer.onMessage(record(campaignId, scheduled(campaignId)), acknowledgment);

        assertThat(redis.opsForHash().get(CampaignProjectionRedisKeys.meta(campaignId), "state"))
                .isEqualTo("SCHEDULED");
        assertThat(redis.opsForHash().get(CampaignProjectionRedisKeys.stock(campaignId),
                scheduledVariantId.toString())).isEqualTo("20");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void activationBeforeScheduleIsRepairedByControlPlaneSnapshotRecovery() {
        UUID campaignId = UUID.randomUUID();
        CampaignProjectionService projectionService = new CampaignProjectionService(store, recoveryQueue);
        CampaignLifecycleKafkaConsumer consumer = new CampaignLifecycleKafkaConsumer(
                new CampaignLifecycleAvroMapper(), projectionService, projectionService);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        consumer.onMessage(record(campaignId, activated(campaignId)), acknowledgment);

        assertThat(recoveryQueue.loadDue(END.plusSeconds(1))).contains(campaignId);
        CampaignSaleProjection recovered = projection(campaignId, 2, CampaignProjectionState.SCHEDULED);
        LoadCampaignSnapshotPort snapshots = ignored -> java.util.Optional.of(recovered);
        CampaignProjectionRecoveryService recovery = new CampaignProjectionRecoveryService(
                snapshots, store, recoveryQueue);

        recovery.recover(new com.philia.flashsale.flashsale.campaignprojection.application.command.RecoverCampaignProjectionCommand(
                campaignId, Instant.now()));

        assertThat(redis.opsForHash().get(CampaignProjectionRedisKeys.meta(campaignId), "state"))
                .isEqualTo("SCHEDULED");
        assertThat(recoveryQueue.loadDue(END.plusSeconds(1))).doesNotContain(campaignId);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void RedisFailureLeavesKafkaOffsetUnacknowledgedAndKeepsAdmissionClosed() {
        connectionFactory.destroy();
        UUID campaignId = UUID.randomUUID();
        CampaignProjectionService projectionService = new CampaignProjectionService(store, recoveryQueue);
        CampaignLifecycleKafkaConsumer consumer = new CampaignLifecycleKafkaConsumer(
                new CampaignLifecycleAvroMapper(), projectionService, projectionService);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);

        assertThatThrownBy(() -> consumer.onMessage(record(campaignId, scheduled(campaignId)), acknowledgment))
                .isInstanceOf(RuntimeException.class);

        verify(acknowledgment, never()).acknowledge();
    }

    private ConsumerRecord<String, SpecificRecord> record(UUID campaignId, SpecificRecord value) {
        return new ConsumerRecord<>(TOPIC, 0, 0L, campaignId.toString(), value);
    }

    private UUID scheduledVariantId;

    private CampaignScheduledV1 scheduled(UUID campaignId) {
        scheduledVariantId = UUID.randomUUID();
        return new CampaignScheduledV1(UUID.randomUUID(), "CampaignScheduled", 1, "CAMPAIGN", campaignId,
                1L, START, new CampaignScheduledDataV1(campaignId, "G4", START, END,
                new CampaignScheduledItemV1(scheduledVariantId, UUID.randomUUID(), "SKU-G4",
                        new BigDecimal("19.9900"), "VND", 20L, 2L)));
    }

    private CampaignActivatedV1 activated(UUID campaignId) {
        return new CampaignActivatedV1(UUID.randomUUID(), "CampaignActivated", 1, "CAMPAIGN", campaignId,
                2L, START, new CampaignActivatedDataV1(campaignId, START, END));
    }

    private CampaignSaleProjection projection(UUID campaignId, long version, CampaignProjectionState state) {
        return CampaignSaleProjection.recovered(campaignId, version, state, START, END,
                new CampaignItemProjection(UUID.randomUUID(), UUID.randomUUID(), "SKU-G4",
                        new BigDecimal("19.9900"), "VND", 20, 2), START);
    }
}
