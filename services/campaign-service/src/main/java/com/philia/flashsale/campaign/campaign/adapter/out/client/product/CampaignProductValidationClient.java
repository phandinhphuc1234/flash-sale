package com.philia.flashsale.campaign.campaign.adapter.out.client.product;

import com.philia.flashsale.campaign.campaign.application.exception.CampaignDownstreamException;
import com.philia.flashsale.campaign.campaign.application.exception.CampaignDownstreamException.Failure;
import com.philia.flashsale.campaign.campaign.application.port.out.ValidateCampaignVariantPort;
import com.philia.flashsale.campaign.campaign.application.result.ValidatedCampaignVariant;
import com.philia.flashsale.campaign.security.serviceidentity.CampaignServiceTokenException;
import com.philia.flashsale.campaign.security.serviceidentity.CampaignServiceTokenManager;
import com.philia.flashsale.campaign.security.serviceidentity.CampaignServiceTokenManager.Capability;
import com.philia.flashsale.campaign.websupport.context.CampaignRequestContext;
import feign.FeignException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Product outbound adapter that owns service identity, trace, mapping, and transport failures. */
@Component
public final class CampaignProductValidationClient implements ValidateCampaignVariantPort {

    private static final Logger LOG = LoggerFactory.getLogger(CampaignProductValidationClient.class);

    private final ProductFeignClient client;
    private final ProductValidationClientMapper mapper;
    private final CampaignServiceTokenManager tokenManager;

    public CampaignProductValidationClient(
            ProductFeignClient client,
            ProductValidationClientMapper mapper,
            CampaignServiceTokenManager tokenManager) {
        this.client = client;
        this.mapper = mapper;
        this.tokenManager = tokenManager;
    }

    @Override
    public ValidatedCampaignVariant validate(UUID variantId, String traceId) {
        Objects.requireNonNull(variantId, "variantId is required");
        String safeTraceId = CampaignRequestContext.normalizeOrGenerate(traceId);
        try {
            ProductValidationResponse response = client.validate(
                    tokenManager.authorizationHeader(Capability.PRODUCT_VALIDATION),
                    safeTraceId,
                    new ProductValidationRequest(variantId));
            verifyIdentity(variantId, response);
            return mapper.toResult(response);
        } catch (ProductRemoteException exception) {
            if (exception.failure() == ProductRemoteException.Failure.TOKEN_REJECTED) {
                LOG.warn("campaign_product_service_token_rejected traceId={} status={}",
                        safeTraceId, exception.status());
            }
            throw mapFailure(exception.failure());
        } catch (CampaignServiceTokenException | FeignException exception) {
            LOG.warn("campaign_product_unavailable traceId={} failureType={}",
                    safeTraceId, exception.getClass().getSimpleName());
            throw new CampaignDownstreamException(Failure.PRODUCT_SERVICE_UNAVAILABLE);
        }
    }

    private void verifyIdentity(UUID expectedVariantId, ProductValidationResponse response) {
        if (response == null || !expectedVariantId.equals(response.variantId())
                || response.productId() == null) {
            throw new CampaignDownstreamException(Failure.PRODUCT_SERVICE_UNAVAILABLE);
        }
    }

    private CampaignDownstreamException mapFailure(ProductRemoteException.Failure failure) {
        return switch (failure) {
            case VALIDATION -> new CampaignDownstreamException(Failure.CAMPAIGN_VALIDATION_FAILED);
            case VARIANT_NOT_FOUND -> new CampaignDownstreamException(Failure.PRODUCT_VARIANT_NOT_FOUND);
            case VARIANT_NOT_SELLABLE -> new CampaignDownstreamException(Failure.PRODUCT_VARIANT_NOT_SELLABLE);
            case TOKEN_REJECTED, UNAVAILABLE ->
                    new CampaignDownstreamException(Failure.PRODUCT_SERVICE_UNAVAILABLE);
        };
    }
}
