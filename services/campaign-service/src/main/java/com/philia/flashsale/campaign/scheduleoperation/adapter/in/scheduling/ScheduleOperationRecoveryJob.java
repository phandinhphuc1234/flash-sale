package com.philia.flashsale.campaign.scheduleoperation.adapter.in.scheduling;

import com.philia.flashsale.campaign.campaign.application.command.ScheduleCampaignCommand;
import com.philia.flashsale.campaign.campaign.application.port.in.ScheduleCampaignUseCase;
import com.philia.flashsale.campaign.scheduleoperation.application.port.out.LoadResumableScheduleOperationsPort;
import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Resumes durable schedule operations with their original idempotency and trace identities. */
@Component
public final class ScheduleOperationRecoveryJob {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduleOperationRecoveryJob.class);

    private final LoadResumableScheduleOperationsPort operationPort;
    private final ScheduleCampaignUseCase scheduleUseCase;

    public ScheduleOperationRecoveryJob(
            LoadResumableScheduleOperationsPort operationPort,
            ScheduleCampaignUseCase scheduleUseCase) {
        this.operationPort = operationPort;
        this.scheduleUseCase = scheduleUseCase;
    }

    @Scheduled(fixedDelayString = "${flashsale.campaign.lifecycle.scan-delay:2s}")
    public void recover() {
        for (ScheduleOperation operation : operationPort.findResumable(100)) {
            try {
                scheduleUseCase.schedule(new ScheduleCampaignCommand(
                        operation.campaignId(), operation.campaignVersion(),
                        operation.idempotencyKey(), operation.callerService(), operation.traceId()));
            } catch (RuntimeException exception) {
                Throwable cause = exception.getCause();
                LOG.warn(
                        "campaign_schedule_recovery_deferred campaignId={} operationId={} "
                                + "failureType={} causeType={} message={}",
                        operation.campaignId(), operation.id(),
                        exception.getClass().getSimpleName(),
                        cause == null ? "none" : cause.getClass().getSimpleName(),
                        exception.getMessage(), exception);
            }
        }
    }
}
