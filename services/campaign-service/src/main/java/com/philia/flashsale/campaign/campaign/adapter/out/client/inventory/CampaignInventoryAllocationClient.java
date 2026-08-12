package com.philia.flashsale.campaign.campaign.adapter.out.client.inventory;

import com.philia.flashsale.campaign.campaign.application.command.AllocateCampaignInventoryCommand;
import com.philia.flashsale.campaign.campaign.application.exception.CampaignDownstreamException;
import com.philia.flashsale.campaign.campaign.application.exception.CampaignDownstreamException.Failure;
import com.philia.flashsale.campaign.campaign.application.port.out.AllocateCampaignInventoryPort;
import com.philia.flashsale.campaign.campaign.application.result.CampaignInventoryAllocation;
import com.philia.flashsale.campaign.security.serviceidentity.CampaignServiceTokenException;
import com.philia.flashsale.campaign.security.serviceidentity.CampaignServiceTokenManager;
import com.philia.flashsale.campaign.security.serviceidentity.CampaignServiceTokenManager.Capability;
import com.philia.flashsale.campaign.websupport.context.CampaignRequestContext;
import com.philia.flashsale.common.web.ApiResponse;
import feign.FeignException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Inventory outbound adapter that preserves request identity and rejects incompatible responses. */
@Component
public final class CampaignInventoryAllocationClient implements AllocateCampaignInventoryPort {

    private static final Logger LOG = LoggerFactory.getLogger(CampaignInventoryAllocationClient.class);

    private final InventoryFeignClient client;
    private final InventoryAllocationClientMapper mapper;
    private final CampaignServiceTokenManager tokenManager;

    public CampaignInventoryAllocationClient(
            InventoryFeignClient client,
            InventoryAllocationClientMapper mapper,
            CampaignServiceTokenManager tokenManager) {
        this.client = client;
        this.mapper = mapper;
        this.tokenManager = tokenManager;
    }

    @Override
    public CampaignInventoryAllocation allocate(
            AllocateCampaignInventoryCommand command, String traceId) {
        Objects.requireNonNull(command, "command is required");
        String safeTraceId = CampaignRequestContext.normalizeOrGenerate(traceId);
        try {
            ApiResponse<InventoryAllocationResponse> envelope = client.allocate(
                    tokenManager.authorizationHeader(Capability.INVENTORY_ALLOCATION),
                    safeTraceId,
                    mapper.toRequest(command));
            InventoryAllocationResponse response = verifyResult(command, envelope);
            return mapper.toResult(response);
        } catch (InventoryRemoteException exception) {
            if (exception.failure() == InventoryRemoteException.Failure.TOKEN_REJECTED) {
                LOG.warn("campaign_inventory_service_token_rejected traceId={} status={}",
                        safeTraceId, exception.status());
            }
            throw mapFailure(exception.failure());
        } catch (CampaignServiceTokenException | FeignException exception) {
            LOG.warn("campaign_inventory_unavailable traceId={} failureType={}",
                    safeTraceId, exception.getClass().getSimpleName());
            throw new CampaignDownstreamException(Failure.INVENTORY_SERVICE_UNAVAILABLE);
        }
    }

    private InventoryAllocationResponse verifyResult(
            AllocateCampaignInventoryCommand command,
            ApiResponse<InventoryAllocationResponse> envelope) {
        InventoryAllocationResponse response = envelope == null ? null : envelope.data();
        boolean compatible = envelope != null && envelope.success() && response != null
                && response.id() != null
                && command.requestId().equals(response.requestId())
                && command.campaignId().equals(response.campaignId())
                && command.variantId().equals(response.variantId())
                && command.quantity() == response.allocatedQuantity()
                && "ACTIVE".equals(response.status());
        if (!compatible) {
            throw new CampaignDownstreamException(Failure.INVENTORY_ALLOCATION_REJECTED);
        }
        return response;
    }

    private CampaignDownstreamException mapFailure(InventoryRemoteException.Failure failure) {
        return switch (failure) {
            case INSUFFICIENT_STOCK ->
                    new CampaignDownstreamException(Failure.INVENTORY_INSUFFICIENT_STOCK);
            case REQUEST_CONFLICT ->
                    new CampaignDownstreamException(Failure.INVENTORY_ALLOCATION_CONFLICT);
            case NOT_FOUND, REJECTED ->
                    new CampaignDownstreamException(Failure.INVENTORY_ALLOCATION_REJECTED);
            case TOKEN_REJECTED, UNAVAILABLE ->
                    new CampaignDownstreamException(Failure.INVENTORY_SERVICE_UNAVAILABLE);
        };
    }
}
