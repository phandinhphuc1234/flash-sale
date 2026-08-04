package com.philia.flashsale.campaign.campaign.application.usecase;

import com.philia.flashsale.campaign.campaign.application.command.ActivateCampaignCommand;
import com.philia.flashsale.campaign.campaign.application.command.EndCampaignCommand;
import com.philia.flashsale.campaign.campaign.application.exception.CampaignNotFoundException;
import com.philia.flashsale.campaign.campaign.application.exception.CampaignVersionConflictException;
import com.philia.flashsale.campaign.campaign.application.port.in.ActivateCampaignUseCase;
import com.philia.flashsale.campaign.campaign.application.port.in.EndCampaignUseCase;
import com.philia.flashsale.campaign.campaign.application.port.out.LoadCampaignPort;
import com.philia.flashsale.campaign.campaign.application.port.out.TransitionCampaignPort;
import com.philia.flashsale.campaign.campaign.application.result.CampaignDetailResult;
import com.philia.flashsale.campaign.campaign.application.result.CampaignLifecycleResult;
import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import com.philia.flashsale.campaign.campaign.domain.policy.CampaignLifecyclePolicy;
import java.util.Objects;

/** Coordinates domain lifecycle rules with an atomic persistence boundary. */
public final class CampaignLifecycleService implements ActivateCampaignUseCase, EndCampaignUseCase {

    private final LoadCampaignPort campaignPort;
    private final TransitionCampaignPort transitionPort;
    private final CampaignLifecyclePolicy lifecyclePolicy;

    public CampaignLifecycleService(
            LoadCampaignPort campaignPort,
            TransitionCampaignPort transitionPort,
            CampaignLifecyclePolicy lifecyclePolicy) {
        this.campaignPort = Objects.requireNonNull(campaignPort);
        this.transitionPort = Objects.requireNonNull(transitionPort);
        this.lifecyclePolicy = Objects.requireNonNull(lifecyclePolicy);
    }

    @Override
    public CampaignLifecycleResult activate(ActivateCampaignCommand command) {
        Objects.requireNonNull(command, "Activation command is required");
        Campaign current = load(command.campaignId());
        requireExpectedVersion(current, command.expectedVersion());
        lifecyclePolicy.ensureCanActivate(current, command.now());

        current.markActive(command.actor(), command.now());
        return transitionPort.activate(current, command)
                .map(committed -> new CampaignLifecycleResult(CampaignDetailResult.from(committed), true))
                .orElseGet(() -> lostActivation(command, current));
    }

    @Override
    public CampaignLifecycleResult end(EndCampaignCommand command) {
        Objects.requireNonNull(command, "Ending command is required");
        Campaign current = load(command.campaignId());
        requireExpectedVersion(current, command.expectedVersion());
        lifecyclePolicy.ensureCanEnd(current, command.now());

        current.markEnded(command.actor(), command.now());
        return transitionPort.end(current, command)
                .map(committed -> new CampaignLifecycleResult(CampaignDetailResult.from(committed), true))
                .orElseGet(() -> new CampaignLifecycleResult(
                        CampaignDetailResult.from(load(command.campaignId())), false));
    }

    private CampaignLifecycleResult lostActivation(ActivateCampaignCommand command, Campaign candidate) {
        Campaign latest = load(command.campaignId());
        if (command.manualRecovery()) {
            throw new CampaignVersionConflictException(
                    command.campaignId(), command.expectedVersion(), latest.version());
        }
        return new CampaignLifecycleResult(CampaignDetailResult.from(latest), false);
    }

    private Campaign load(java.util.UUID campaignId) {
        return campaignPort.findById(campaignId)
                .orElseThrow(() -> new CampaignNotFoundException(campaignId));
    }

    private void requireExpectedVersion(Campaign campaign, long expectedVersion) {
        if (campaign.version() != expectedVersion) {
            throw new CampaignVersionConflictException(
                    campaign.id(), expectedVersion, campaign.version());
        }
    }
}
