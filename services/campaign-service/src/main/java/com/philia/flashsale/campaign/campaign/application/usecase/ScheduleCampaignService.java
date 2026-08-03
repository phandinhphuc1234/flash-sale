package com.philia.flashsale.campaign.campaign.application.usecase;

import com.philia.flashsale.campaign.campaign.application.command.AllocateCampaignInventoryCommand;
import com.philia.flashsale.campaign.campaign.application.command.FinalizeCampaignSchedulingCommand;
import com.philia.flashsale.campaign.campaign.application.command.ScheduleCampaignCommand;
import com.philia.flashsale.campaign.campaign.application.exception.CampaignDownstreamException;
import com.philia.flashsale.campaign.campaign.application.exception.CampaignNotFoundException;
import com.philia.flashsale.campaign.campaign.application.exception.CampaignVersionConflictException;
import com.philia.flashsale.campaign.campaign.application.port.in.ScheduleCampaignUseCase;
import com.philia.flashsale.campaign.campaign.application.port.out.AllocateCampaignInventoryPort;
import com.philia.flashsale.campaign.campaign.application.port.out.CampaignActorPort;
import com.philia.flashsale.campaign.campaign.application.port.out.CampaignClockPort;
import com.philia.flashsale.campaign.campaign.application.port.out.FinalizeCampaignSchedulingPort;
import com.philia.flashsale.campaign.campaign.application.port.out.LoadCampaignPort;
import com.philia.flashsale.campaign.campaign.application.port.out.ValidateCampaignVariantPort;
import com.philia.flashsale.campaign.campaign.application.result.CampaignDetailResult;
import com.philia.flashsale.campaign.campaign.application.result.CampaignInventoryAllocation;
import com.philia.flashsale.campaign.campaign.application.result.ValidatedCampaignVariant;
import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import com.philia.flashsale.campaign.scheduleoperation.application.command.PrepareScheduleOperationCommand;
import com.philia.flashsale.campaign.scheduleoperation.application.port.in.PrepareScheduleOperationUseCase;
import com.philia.flashsale.campaign.scheduleoperation.application.port.out.LoadScheduleOperationPort;
import com.philia.flashsale.campaign.scheduleoperation.application.port.out.SaveScheduleOperationPort;
import com.philia.flashsale.campaign.scheduleoperation.application.result.ScheduleOperationPreparationResult;
import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperation;
import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperationFingerprint;
import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperationStatus;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Orchestrates Product and Inventory calls without holding a database transaction across them.
 * Only the final aggregate/outbox transition is delegated to the transactional persistence port.
 */
@Service
public final class ScheduleCampaignService implements ScheduleCampaignUseCase {

    private final LoadCampaignPort campaignPort;
    private final LoadScheduleOperationPort operationPort;
    private final SaveScheduleOperationPort operationSavePort;
    private final PrepareScheduleOperationUseCase operationPreparation;
    private final ValidateCampaignVariantPort productPort;
    private final AllocateCampaignInventoryPort inventoryPort;
    private final FinalizeCampaignSchedulingPort finalizationPort;
    private final CampaignActorPort actorPort;
    private final CampaignClockPort clockPort;

    public ScheduleCampaignService(
            LoadCampaignPort campaignPort,
            LoadScheduleOperationPort operationPort,
            SaveScheduleOperationPort operationSavePort,
            PrepareScheduleOperationUseCase operationPreparation,
            ValidateCampaignVariantPort productPort,
            AllocateCampaignInventoryPort inventoryPort,
            FinalizeCampaignSchedulingPort finalizationPort,
            CampaignActorPort actorPort,
            CampaignClockPort clockPort) {
        this.campaignPort = Objects.requireNonNull(campaignPort);
        this.operationPort = Objects.requireNonNull(operationPort);
        this.operationSavePort = Objects.requireNonNull(operationSavePort);
        this.operationPreparation = Objects.requireNonNull(operationPreparation);
        this.productPort = Objects.requireNonNull(productPort);
        this.inventoryPort = Objects.requireNonNull(inventoryPort);
        this.finalizationPort = Objects.requireNonNull(finalizationPort);
        this.actorPort = Objects.requireNonNull(actorPort);
        this.clockPort = Objects.requireNonNull(clockPort);
    }

    @Override
    public CampaignDetailResult schedule(ScheduleCampaignCommand command) {
        Objects.requireNonNull(command, "Schedule command is required");
        Campaign campaign = campaignPort.findById(command.campaignId())
                .orElseThrow(() -> new CampaignNotFoundException(command.campaignId()));

        var existing = operationPort.findByCampaignIdAndIdempotencyKey(
                command.campaignId(), command.idempotencyKey());
        if (existing.isEmpty() && campaign.version() != command.expectedVersion()) {
            throw new CampaignVersionConflictException(
                    campaign.id(), command.expectedVersion(), campaign.version());
        }
        if (existing.isEmpty() && !campaign.isEditable()) {
            throw new IllegalStateException("Only a draft Campaign can be scheduled");
        }

        ScheduleOperationFingerprint fingerprint = fingerprint(campaign, command.expectedVersion());
        ScheduleOperationPreparationResult preparation = operationPreparation.prepare(
                new PrepareScheduleOperationCommand(
                        campaign.id(),
                        command.idempotencyKey(),
                        fingerprint,
                        command.expectedVersion(),
                        actorPort.currentActor(),
                        command.callerService(),
                        command.traceId()));
        ScheduleOperation operation = preparation.operation();

        if (operation.status() == ScheduleOperationStatus.COMPLETED) {
            return CampaignDetailResult.from(campaignPort.findById(campaign.id())
                    .orElseThrow(() -> new CampaignNotFoundException(campaign.id())));
        }

        ValidatedCampaignVariant product = productPort.validate(campaign.item().variantId(), command.traceId());
        if (!product.sellable()) {
            failBusinessOperation(operation, CampaignDownstreamException.Failure.PRODUCT_VARIANT_NOT_SELLABLE);
        }

        CampaignInventoryAllocation allocation;
        try {
            // The operation's stable request id makes this retry an idempotent read-or-allocate call.
            allocation = inventoryPort.allocate(
                    new AllocateCampaignInventoryCommand(
                            operation.inventoryRequestId(),
                            campaign.id(),
                            campaign.item().variantId(),
                            campaign.item().requestedQuantity()),
                    command.traceId());
        } catch (CampaignDownstreamException exception) {
            if (isTerminal(exception.failure())) {
                failBusinessOperation(operation, exception.failure());
            }
            throw exception;
        }

        if (operation.status() == ScheduleOperationStatus.STARTED) {
            operation.markInventoryAllocated();
            operationSavePort.save(operation);
        }

        Campaign finalized = finalizationPort.finalizeSchedule(
                new FinalizeCampaignSchedulingCommand(
                        campaign.id(),
                        operation.id(),
                        command.expectedVersion(),
                        product,
                        allocation,
                        actorPort.currentActor(),
                        command.traceId(),
                        clockPort.now()));
        return CampaignDetailResult.from(finalized);
    }

    private void failBusinessOperation(ScheduleOperation operation,
            CampaignDownstreamException.Failure failure) {
        if (operation.status() == ScheduleOperationStatus.STARTED) {
            operation.markFailed(failure.name(), failure.message());
            operationSavePort.save(operation);
        }
        throw new CampaignDownstreamException(failure);
    }

    private boolean isTerminal(CampaignDownstreamException.Failure failure) {
        return failure != CampaignDownstreamException.Failure.PRODUCT_SERVICE_UNAVAILABLE
                && failure != CampaignDownstreamException.Failure.INVENTORY_SERVICE_UNAVAILABLE;
    }

    private ScheduleOperationFingerprint fingerprint(Campaign campaign, long expectedVersion) {
        var item = campaign.item();
        String canonical = String.join("|",
                campaign.id().toString(),
                Long.toString(expectedVersion),
                campaign.code(),
                campaign.name(),
                campaign.startAt().toString(),
                campaign.endAt().toString(),
                item == null ? "" : item.variantId().toString(),
                item == null ? "" : item.campaignPrice().amount().toPlainString(),
                item == null ? "" : item.campaignPrice().currency(),
                item == null ? "" : Long.toString(item.requestedQuantity()),
                item == null ? "" : Long.toString(item.purchaseLimitPerUser()));
        return ScheduleOperationFingerprint.fromCanonicalPayload(canonical);
    }
}
