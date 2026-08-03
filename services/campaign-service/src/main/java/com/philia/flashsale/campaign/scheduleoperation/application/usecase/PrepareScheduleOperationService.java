package com.philia.flashsale.campaign.scheduleoperation.application.usecase;

import com.philia.flashsale.campaign.scheduleoperation.application.command.PrepareScheduleOperationCommand;
import com.philia.flashsale.campaign.scheduleoperation.application.exception.ScheduleOperationInProgressException;
import com.philia.flashsale.campaign.scheduleoperation.application.exception.ScheduleOperationRequestConflictException;
import com.philia.flashsale.campaign.scheduleoperation.application.port.in.PrepareScheduleOperationUseCase;
import com.philia.flashsale.campaign.scheduleoperation.application.port.out.LoadScheduleOperationPort;
import com.philia.flashsale.campaign.scheduleoperation.application.port.out.LoadLockedScheduleOperationPort;
import com.philia.flashsale.campaign.scheduleoperation.application.port.out.SaveScheduleOperationPort;
import com.philia.flashsale.campaign.scheduleoperation.application.port.out.ScheduleOperationClockPort;
import com.philia.flashsale.campaign.scheduleoperation.application.result.ScheduleOperationPreparationResult;
import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperation;
import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperationStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the short local transaction that creates, replays, or reopens one schedule operation.
 *
 * <p>No Product, Inventory, token, or other remote call belongs inside this method. T058 consumes
 * the returned identity after this transaction has committed.</p>
 */
public class PrepareScheduleOperationService implements PrepareScheduleOperationUseCase {

    private final LoadLockedScheduleOperationPort loadPort;
    private final SaveScheduleOperationPort savePort;
    private final ScheduleOperationClockPort clockPort;

    public PrepareScheduleOperationService(
            LoadLockedScheduleOperationPort loadPort,
            SaveScheduleOperationPort savePort,
            ScheduleOperationClockPort clockPort) {
        this.loadPort = Objects.requireNonNull(loadPort, "Schedule operation load port is required");
        this.savePort = Objects.requireNonNull(savePort, "Schedule operation save port is required");
        this.clockPort = Objects.requireNonNull(clockPort, "Schedule operation clock port is required");
    }

    @Override
    @Transactional
    public ScheduleOperationPreparationResult prepare(PrepareScheduleOperationCommand command) {
        Objects.requireNonNull(command, "Prepare schedule operation command is required");

        var existing = loadPort.findLockedByCampaignIdAndIdempotencyKey(
                command.campaignId(), command.idempotencyKey());
        if (existing.isPresent()) {
            return handleExisting(existing.get(), command);
        }

        if (loadPort.existsInFlightByCampaignId(command.campaignId())) {
            throw new ScheduleOperationInProgressException(command.campaignId());
        }

        Instant now = requiredNow();
        ScheduleOperation operation = ScheduleOperation.start(
                UUID.randomUUID(),
                command.campaignId(),
                command.idempotencyKey(),
                UUID.randomUUID(),
                command.fingerprint(),
                command.campaignVersion(),
                command.initiatedBy(),
                command.callerService(),
                command.traceId(),
                now);
        return new ScheduleOperationPreparationResult(savePort.save(operation), false);
    }

    private ScheduleOperationPreparationResult handleExisting(
            ScheduleOperation operation, PrepareScheduleOperationCommand command) {
        if (!operation.matches(
                command.idempotencyKey(), command.fingerprint(), command.campaignVersion())) {
            throw new ScheduleOperationRequestConflictException(command.campaignId());
        }
        if (operation.status() == ScheduleOperationStatus.FAILED) {
            operation.retry(requiredNow());
            return new ScheduleOperationPreparationResult(savePort.save(operation), true);
        }
        return new ScheduleOperationPreparationResult(operation, true);
    }

    private Instant requiredNow() {
        return Objects.requireNonNull(clockPort.now(), "Schedule operation clock returned null");
    }
}
