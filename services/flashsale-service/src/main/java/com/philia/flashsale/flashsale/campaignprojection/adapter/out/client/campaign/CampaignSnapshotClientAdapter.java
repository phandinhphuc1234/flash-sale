package com.philia.flashsale.flashsale.campaignprojection.adapter.out.client.campaign;

import com.philia.flashsale.flashsale.campaignprojection.application.exception.CampaignSnapshotRecoveryException;
import com.philia.flashsale.flashsale.campaignprojection.application.port.out.LoadCampaignSnapshotPort;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignSaleProjection;
import com.philia.flashsale.flashsale.observability.FlashSaleTraceContext;
import com.philia.flashsale.flashsale.observability.FlashSaleObservability;
import com.philia.flashsale.flashsale.security.serviceidentity.FlashSaleServiceTokenException;
import feign.FeignException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Recovery-only Campaign HTTP adapter; it is never invoked by the shopper reservation path. */
@Component
@ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
public final class CampaignSnapshotClientAdapter implements LoadCampaignSnapshotPort {
    private final CampaignSnapshotFeignClient client;
    private final CampaignSnapshotClientMapper mapper;
    private final FlashSaleObservability observability;

    public CampaignSnapshotClientAdapter(CampaignSnapshotFeignClient client,
            CampaignSnapshotClientMapper mapper) {
        this(client, mapper, FlashSaleObservability.noop());
    }

    @Autowired
    public CampaignSnapshotClientAdapter(CampaignSnapshotFeignClient client,
            CampaignSnapshotClientMapper mapper, FlashSaleObservability observability) {
        this.client = Objects.requireNonNull(client);
        this.mapper = Objects.requireNonNull(mapper);
        this.observability = Objects.requireNonNull(observability);
    }

    @Override
    public Optional<CampaignSaleProjection> load(UUID campaignId) {
        return observability.observe(FlashSaleObservability.Operation.CAMPAIGN_RECOVERY,
                () -> loadSnapshot(campaignId));
    }

    private Optional<CampaignSaleProjection> loadSnapshot(UUID campaignId) {
        Objects.requireNonNull(campaignId, "campaignId");
        String traceparent = FlashSaleTraceContext.currentOrGenerate();
        String traceId = traceparent.substring(3, 35);
        try {
            CampaignSnapshotClientResponse response = client.getSnapshot(
                    campaignId, MediaType.APPLICATION_JSON_VALUE, traceparent, traceId);
            if (response == null || !campaignId.equals(response.campaignId())) {
                throw new CampaignSnapshotRecoveryException(
                        CampaignSnapshotRecoveryException.Failure.INVALID_RESPONSE,
                        Duration.ofSeconds(5));
            }
            return Optional.of(mapper.toProjection(response));
        } catch (CampaignSnapshotRecoveryException exception) {
            throw exception;
        } catch (CampaignSnapshotRemoteException exception) {
            throw new CampaignSnapshotRecoveryException(
                    CampaignSnapshotRecoveryException.Failure.valueOf(exception.failure().name()),
                    exception.retryAfter());
        } catch (FlashSaleServiceTokenException | FeignException exception) {
            throw new CampaignSnapshotRecoveryException(
                    CampaignSnapshotRecoveryException.Failure.UNAVAILABLE,
                    Duration.ofSeconds(5), exception);
        }
    }
}
