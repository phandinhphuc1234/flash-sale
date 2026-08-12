package com.philia.flashsale.flashsale.campaignprojection.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignActivatedV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledV1;
import com.philia.flashsale.flashsale.campaignprojection.application.port.in.ProjectCampaignActivationUseCase;
import com.philia.flashsale.flashsale.campaignprojection.application.port.in.ProjectCampaignScheduleUseCase;
import com.philia.flashsale.flashsale.observability.FlashSaleTraceContext;
import com.philia.flashsale.flashsale.websupport.context.FlashSaleRequestContext;
import java.nio.charset.StandardCharsets;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Consumes approved Campaign lifecycle facts and acknowledges only after Redis succeeds. */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public final class CampaignLifecycleKafkaConsumer {

    private final CampaignLifecycleAvroMapper mapper;
    private final ProjectCampaignScheduleUseCase scheduleUseCase;
    private final ProjectCampaignActivationUseCase activationUseCase;

    public CampaignLifecycleKafkaConsumer(CampaignLifecycleAvroMapper mapper,
            ProjectCampaignScheduleUseCase scheduleUseCase,
            ProjectCampaignActivationUseCase activationUseCase) {
        this.mapper = mapper;
        this.scheduleUseCase = scheduleUseCase;
        this.activationUseCase = activationUseCase;
    }

    @KafkaListener(
            topics = "${flashsale.campaign-projection.topic}",
            groupId = "${flashsale.campaign-projection.consumer-group}",
            containerFactory = "campaignProjectionKafkaListenerContainerFactory")
    public void onMessage(ConsumerRecord<String, SpecificRecord> record, Acknowledgment acknowledgment) {
        String traceparent = header(record, FlashSaleRequestContext.TRACEPARENT_HEADER);
        String traceId = validTraceId(traceparent);
        try (MDC.MDCCloseable trace = MDC.putCloseable("traceId", traceId);
                MDC.MDCCloseable parent = MDC.putCloseable(
                        FlashSaleRequestContext.TRACEPARENT_HEADER,
                        FlashSaleRequestContext.isValidTraceparent(traceparent)
                                ? traceparent : FlashSaleTraceContext.currentOrGenerate())) {
            SpecificRecord value = record.value();
            if (value instanceof CampaignScheduledV1 scheduled) {
                scheduleUseCase.project(mapper.toScheduled(record.key(), scheduled));
            } else if (value instanceof CampaignActivatedV1 activated) {
                activationUseCase.project(mapper.toActivated(record.key(), activated));
            } else {
                throw new CampaignLifecycleRecordException("Unsupported Campaign lifecycle record");
            }
            acknowledgment.acknowledge();
        }
    }

    private String header(ConsumerRecord<String, SpecificRecord> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private String validTraceId(String traceparent) {
        return FlashSaleRequestContext.isValidTraceparent(traceparent)
                ? traceparent.substring(3, 35) : FlashSaleTraceContext.currentOrGenerate().substring(3, 35);
    }
}
