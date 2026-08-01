package com.philia.flashsale.campaign.campaign.application.usecase;

import com.philia.flashsale.campaign.campaign.application.command.CreateCampaignCommand;
import com.philia.flashsale.campaign.campaign.application.command.ReplaceCampaignItemCommand;
import com.philia.flashsale.campaign.campaign.application.command.ReplaceCampaignMetadataCommand;
import com.philia.flashsale.campaign.campaign.application.exception.CampaignCodeAlreadyExistsException;
import com.philia.flashsale.campaign.campaign.application.exception.CampaignNotFoundException;
import com.philia.flashsale.campaign.campaign.application.exception.CampaignOperationInProgressException;
import com.philia.flashsale.campaign.campaign.application.exception.CampaignVersionConflictException;
import com.philia.flashsale.campaign.campaign.application.port.in.CreateCampaignUseCase;
import com.philia.flashsale.campaign.campaign.application.port.in.GetCampaignDetailUseCase;
import com.philia.flashsale.campaign.campaign.application.port.in.ReplaceCampaignItemUseCase;
import com.philia.flashsale.campaign.campaign.application.port.in.ReplaceCampaignMetadataUseCase;
import com.philia.flashsale.campaign.campaign.application.port.out.CampaignActorPort;
import com.philia.flashsale.campaign.campaign.application.port.out.CampaignClockPort;
import com.philia.flashsale.campaign.campaign.application.port.out.CheckActiveCampaignOperationPort;
import com.philia.flashsale.campaign.campaign.application.port.out.CheckCampaignCodeUniquenessPort;
import com.philia.flashsale.campaign.campaign.application.port.out.LoadCampaignPort;
import com.philia.flashsale.campaign.campaign.application.port.out.SaveCampaignPort;
import com.philia.flashsale.campaign.campaign.application.query.GetCampaignDetailQuery;
import com.philia.flashsale.campaign.campaign.application.result.CampaignDetailResult;
import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignItem;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** Orchestrates local Campaign draft operations through ports and the aggregate. */
public final class CampaignDraftApplicationService implements
        CreateCampaignUseCase,
        ReplaceCampaignMetadataUseCase,
        ReplaceCampaignItemUseCase,
        GetCampaignDetailUseCase {

    private final LoadCampaignPort loadCampaignPort;
    private final SaveCampaignPort saveCampaignPort;
    private final CheckCampaignCodeUniquenessPort codeUniquenessPort;
    private final CheckActiveCampaignOperationPort activeOperationPort;
    private final CampaignClockPort clockPort;
    private final CampaignActorPort actorPort;

    public CampaignDraftApplicationService(
            LoadCampaignPort loadCampaignPort,
            SaveCampaignPort saveCampaignPort,
            CheckCampaignCodeUniquenessPort codeUniquenessPort,
            CheckActiveCampaignOperationPort activeOperationPort,
            CampaignClockPort clockPort,
            CampaignActorPort actorPort) {
        this.loadCampaignPort = Objects.requireNonNull(loadCampaignPort);
        this.saveCampaignPort = Objects.requireNonNull(saveCampaignPort);
        this.codeUniquenessPort = Objects.requireNonNull(codeUniquenessPort);
        this.activeOperationPort = Objects.requireNonNull(activeOperationPort);
        this.clockPort = Objects.requireNonNull(clockPort);
        this.actorPort = Objects.requireNonNull(actorPort);
    }

    @Override
    @Transactional
    public CampaignDetailResult create(CreateCampaignCommand command) {
        Objects.requireNonNull(command, "Create Campaign command is required");
        Campaign draft = Campaign.createDraft(
                null,
                command.code(),
                command.name(),
                command.startAt(),
                command.endAt(),
                actorPort.currentActor(),
                clockPort.now());
        if (codeUniquenessPort.existsByCode(draft.code())) {
            throw new CampaignCodeAlreadyExistsException(draft.code());
        }
        return CampaignDetailResult.from(saveCampaignPort.save(draft));
    }

    @Override
    @Transactional
    public CampaignDetailResult replaceMetadata(ReplaceCampaignMetadataCommand command) {
        Objects.requireNonNull(command, "Replace metadata command is required");
        Campaign campaign = load(command.campaignId());
        ensureEditableOperation(command.campaignId());
        ensureVersion(command.campaignId(), command.expectedVersion(), campaign);
        campaign.replaceMetadata(
                command.name(),
                command.startAt(),
                command.endAt(),
                actorPort.currentActor(),
                clockPort.now());
        return CampaignDetailResult.from(saveCampaignPort.save(campaign));
    }

    @Override
    @Transactional
    public CampaignDetailResult replaceItem(ReplaceCampaignItemCommand command) {
        Objects.requireNonNull(command, "Replace item command is required");
        Campaign campaign = load(command.campaignId());
        ensureEditableOperation(command.campaignId());
        ensureVersion(command.campaignId(), command.expectedVersion(), campaign);
        CampaignItem item = CampaignItem.createDraft(
                null,
                command.variantId(),
                command.campaignPrice(),
                command.requestedQuantity(),
                command.purchaseLimitPerUser());
        campaign.replaceItem(item, actorPort.currentActor(), clockPort.now());
        return CampaignDetailResult.from(saveCampaignPort.save(campaign));
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignDetailResult getDetail(GetCampaignDetailQuery query) {
        Objects.requireNonNull(query, "Campaign detail query is required");
        return CampaignDetailResult.from(load(query.campaignId()));
    }

    private Campaign load(java.util.UUID campaignId) {
        return loadCampaignPort.findById(campaignId)
                .orElseThrow(() -> new CampaignNotFoundException(campaignId));
    }

    private void ensureEditableOperation(java.util.UUID campaignId) {
        if (activeOperationPort.hasActiveOperation(campaignId)) {
            throw new CampaignOperationInProgressException(campaignId);
        }
    }

    private void ensureVersion(java.util.UUID campaignId, long expectedVersion, Campaign campaign) {
        if (expectedVersion < 0 || campaign.version() != expectedVersion) {
            throw new CampaignVersionConflictException(campaignId, expectedVersion, campaign.version());
        }
    }
}
