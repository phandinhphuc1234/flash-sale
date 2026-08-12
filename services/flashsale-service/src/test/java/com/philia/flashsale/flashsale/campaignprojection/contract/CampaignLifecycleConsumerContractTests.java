package com.philia.flashsale.flashsale.campaignprojection.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignActivatedDataV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignActivatedV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledDataV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledItemV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledV1;
import com.philia.flashsale.flashsale.campaignprojection.adapter.in.messaging.kafka.CampaignLifecycleAvroMapper;
import com.philia.flashsale.flashsale.campaignprojection.adapter.in.messaging.kafka.CampaignLifecycleKafkaConsumer;
import com.philia.flashsale.flashsale.campaignprojection.application.port.in.ProjectCampaignActivationUseCase;
import com.philia.flashsale.flashsale.campaignprojection.application.port.in.ProjectCampaignScheduleUseCase;
import com.philia.flashsale.flashsale.campaignprojection.application.result.CampaignProjectionUpdateResult;
import com.philia.flashsale.flashsale.websupport.context.FlashSaleRequestContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.support.Acknowledgment;

class CampaignLifecycleConsumerContractTests {
    private static final String TOPIC = "campaign.lifecycle.v1";
    private static final Instant OCCURRED_AT = Instant.parse("2030-08-01T10:00:00Z");
    private static final String TRACEPARENT = "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01";

    @Mock
    private ProjectCampaignScheduleUseCase scheduleUseCase;
    @Mock
    private ProjectCampaignActivationUseCase activationUseCase;
    @Mock
    private Acknowledgment acknowledgment;

    private CampaignLifecycleKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        consumer = new CampaignLifecycleKafkaConsumer(new CampaignLifecycleAvroMapper(),
                scheduleUseCase, activationUseCase);
        when(scheduleUseCase.project(any())).thenAnswer(invocation -> {
            assertThat(MDC.get("traceId")).isEqualTo(TRACEPARENT.substring(3, 35));
            assertThat(MDC.get(FlashSaleRequestContext.TRACEPARENT_HEADER)).isEqualTo(TRACEPARENT);
            return CampaignProjectionUpdateResult.APPLIED;
        });
        when(activationUseCase.project(any())).thenAnswer(invocation -> {
            assertThat(MDC.get("traceId")).isEqualTo(TRACEPARENT.substring(3, 35));
            assertThat(MDC.get(FlashSaleRequestContext.TRACEPARENT_HEADER)).isEqualTo(TRACEPARENT);
            return CampaignProjectionUpdateResult.APPLIED;
        });
    }

    @Test
    void mapsApprovedScheduledSpecificRecordAndAcknowledgesAfterUseCaseReturns() {
        UUID campaignId = UUID.randomUUID();
        ConsumerRecord<String, SpecificRecord> record = record(
                campaignId, scheduled(campaignId), TRACEPARENT);

        consumer.onMessage(record, acknowledgment);

        verify(scheduleUseCase).project(any());
        verify(acknowledgment).acknowledge();
        verify(activationUseCase, never()).project(any());
        assertThat(FlashSaleRequestContext.isValidTraceparent(TRACEPARENT)).isTrue();
    }

    @Test
    void mapsApprovedActivationRecordAndAcknowledgesOnlyTheActivationUseCase() {
        UUID campaignId = UUID.randomUUID();

        consumer.onMessage(record(campaignId, activated(campaignId), TRACEPARENT), acknowledgment);

        verify(activationUseCase).project(any());
        verify(scheduleUseCase, never()).project(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void rejectsWrongKeyAndLeavesOffsetUncommitted() {
        UUID campaignId = UUID.randomUUID();

        assertThatThrownBy(() -> consumer.onMessage(
                record(UUID.randomUUID(), scheduled(campaignId), TRACEPARENT), acknowledgment))
                .isInstanceOf(IllegalArgumentException.class);

        verify(acknowledgment, never()).acknowledge();
        verify(scheduleUseCase, never()).project(any());
    }

    @Test
    void rejectsUnsupportedSpecificRecordAndLeavesOffsetUncommitted() {
        UUID campaignId = UUID.randomUUID();
        CampaignActivatedDataV1 unsupported = new CampaignActivatedDataV1(
                campaignId, OCCURRED_AT, OCCURRED_AT.plusSeconds(3600));

        assertThatThrownBy(() -> consumer.onMessage(
                record(campaignId, unsupported, TRACEPARENT), acknowledgment))
                .isInstanceOf(IllegalArgumentException.class);

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void redeliveryAcknowledgesOnlyAfterEachSuccessfulIdempotentProjectionAttempt() {
        UUID campaignId = UUID.randomUUID();
        ConsumerRecord<String, SpecificRecord> record = record(
                campaignId, scheduled(campaignId), TRACEPARENT);

        consumer.onMessage(record, acknowledgment);
        consumer.onMessage(record, acknowledgment);

        verify(scheduleUseCase, org.mockito.Mockito.times(2)).project(any());
        verify(acknowledgment, org.mockito.Mockito.times(2)).acknowledge();
    }

    @Test
    void rejectsInvalidEventVersionBeforeInvokingProjectionUseCase() {
        UUID campaignId = UUID.randomUUID();
        CampaignScheduledV1 invalid = scheduled(campaignId, 2);

        assertThatThrownBy(() -> consumer.onMessage(
                record(campaignId, invalid, TRACEPARENT), acknowledgment))
                .isInstanceOf(IllegalArgumentException.class);

        verify(scheduleUseCase, never()).project(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void rejectsNullDeserializedValueAsPoisonWithoutAcknowledgement() {
        UUID campaignId = UUID.randomUUID();

        assertThatThrownBy(() -> consumer.onMessage(
                record(campaignId, null, TRACEPARENT), acknowledgment))
                .isInstanceOf(IllegalArgumentException.class);

        verify(acknowledgment, never()).acknowledge();
    }

    private ConsumerRecord<String, SpecificRecord> record(
            UUID key, SpecificRecord value, String traceparent) {
        ConsumerRecord<String, SpecificRecord> record = new ConsumerRecord<>(
                TOPIC, 0, 0L, key.toString(), value);
        record.headers().add(FlashSaleRequestContext.TRACEPARENT_HEADER,
                traceparent.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    private CampaignScheduledV1 scheduled(UUID campaignId) {
        return scheduled(campaignId, 1);
    }

    private CampaignScheduledV1 scheduled(UUID campaignId, int eventVersion) {
        return new CampaignScheduledV1(UUID.randomUUID(), "CampaignScheduled", eventVersion, "CAMPAIGN", campaignId,
                1L, OCCURRED_AT, new CampaignScheduledDataV1(campaignId, "G4", OCCURRED_AT,
                OCCURRED_AT.plusSeconds(3600), new CampaignScheduledItemV1(
                        UUID.randomUUID(), UUID.randomUUID(), "SKU-G4", new BigDecimal("19.9900"),
                        "VND", 20L, 2L)));
    }

    private CampaignActivatedV1 activated(UUID campaignId) {
        return new CampaignActivatedV1(UUID.randomUUID(), "CampaignActivated", 1, "CAMPAIGN", campaignId,
                2L, OCCURRED_AT, new CampaignActivatedDataV1(campaignId, OCCURRED_AT,
                OCCURRED_AT.plusSeconds(3600)));
    }
}
