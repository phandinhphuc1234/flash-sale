package com.philia.flashsale.flashsale.campaignprojection.adapter.out.client.campaign;

import com.philia.flashsale.flashsale.campaignprojection.application.exception.CampaignSnapshotRecoveryException;
import com.philia.flashsale.flashsale.campaignprojection.application.port.out.LoadCampaignSnapshotPort;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignSaleProjection;
import com.philia.flashsale.flashsale.observability.FlashSaleTraceContext;
import com.philia.flashsale.flashsale.security.serviceidentity.FlashSaleServiceTokenException;
import feign.FeignException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Recovery-only Campaign HTTP adapter; it is never invoked by the shopper reservation path. */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public final class CampaignSnapshotClientAdapter implements LoadCampaignSnapshotPort {
    private final CampaignSnapshotFeignClient client;
    private final CampaignSnapshotClientMapper mapper;

    public CampaignSnapshotClientAdapter(CampaignSnapshotFeignClient client,
            CampaignSnapshotClientMapper mapper) {
        this.client = Objects.requireNonNull(client);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Optional<CampaignSaleProjection> load(UUID campaignId) {
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
