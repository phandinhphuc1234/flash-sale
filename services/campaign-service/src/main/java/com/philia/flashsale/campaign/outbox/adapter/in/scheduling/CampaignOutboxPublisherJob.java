package com.philia.flashsale.campaign.outbox.adapter.in.scheduling;

import com.philia.flashsale.campaign.campaign.application.port.out.CampaignClockPort;
import com.philia.flashsale.campaign.outbox.application.model.OutboxClaim;
import com.philia.flashsale.campaign.outbox.application.port.out.ClaimDueCampaignOutboxEventsPort;
import com.philia.flashsale.campaign.outbox.application.port.out.MarkCampaignOutboxPublishedPort;
import com.philia.flashsale.campaign.outbox.application.port.out.PublishCampaignOutboxEventPort;
import com.philia.flashsale.campaign.outbox.application.port.out.RecordCampaignOutboxFailurePort;
import com.philia.flashsale.campaign.observability.CampaignObservability;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Relays leased, durable outbox rows to Kafka without holding a database transaction during I/O.
 *
 * <p>The lease owner is checked again when the row is acknowledged or failed. A crashed worker is
 * therefore reclaimed by a later scan and the same event identity may be delivered again, which
 * is the approved at-least-once boundary for this feature.</p>
 */
@Component
public final class CampaignOutboxPublisherJob {

    private static final Logger LOG = LoggerFactory.getLogger(CampaignOutboxPublisherJob.class);
    private final ClaimDueCampaignOutboxEventsPort claimPort;
    private final PublishCampaignOutboxEventPort publisherPort;
    private final MarkCampaignOutboxPublishedPort markPublishedPort;
    private final RecordCampaignOutboxFailurePort failurePort;
    private final CampaignClockPort clockPort;
    private final Duration claimLease;
    private final int batchSize;
    private final int maxAutomaticAttempts;
    private final Duration retryBackoffCap;
    private final String workerId;
    private final CampaignObservability observability;

    public CampaignOutboxPublisherJob(
            ClaimDueCampaignOutboxEventsPort claimPort,
            PublishCampaignOutboxEventPort publisherPort,
            MarkCampaignOutboxPublishedPort markPublishedPort,
            RecordCampaignOutboxFailurePort failurePort,
            CampaignClockPort clockPort,
            @Value("${flashsale.campaign.outbox.claim-lease:30s}") Duration claimLease,
            @Value("${flashsale.campaign.outbox.batch-size:100}") int batchSize,
            @Value("${flashsale.campaign.outbox.max-automatic-attempts:10}") int maxAutomaticAttempts,
            @Value("${flashsale.campaign.outbox.retry-backoff-cap:60s}") Duration retryBackoffCap,
            @Value("${HOSTNAME:campaign-service}") String hostName,
            CampaignObservability observability) {
        this.claimPort = claimPort;
        this.publisherPort = publisherPort;
        this.markPublishedPort = markPublishedPort;
        this.failurePort = failurePort;
        this.clockPort = clockPort;
        this.claimLease = requirePositive(claimLease, "claim lease");
        this.batchSize = requirePositive(batchSize, "batch size");
        this.maxAutomaticAttempts = requirePositive(maxAutomaticAttempts, "maximum automatic attempts");
        this.retryBackoffCap = requirePositive(retryBackoffCap, "retry backoff cap");
        this.workerId = normalizeWorkerId(hostName) + "-" + UUID.randomUUID();
        this.observability = observability;
    }

    /** Runs the approved 500-ms, batch-100 relay scan. */
    @Scheduled(fixedDelayString = "${flashsale.campaign.outbox.scan-delay:500ms}")
    public void publishDue() {
        Instant now = clockPort.now();
        List<OutboxClaim> claims;
        try {
            claims = claimPort.claimDue(workerId, now, claimLease, batchSize);
        } catch (RuntimeException exception) {
            // A database outage must not terminate Spring's scheduled executor; the next scan retries.
            observability.outbox("claim_failure");
            LOG.warn("campaign_outbox_claim_failed failureType={}", exception.getClass().getSimpleName());
            return;
        }
        observability.outbox("claimed");
        for (OutboxClaim claim : claims) {
            publishOne(claim);
        }
    }

    private void publishOne(OutboxClaim claim) {
        observability.withTrace(claim.traceId(), () -> {
            try {
                publisherPort.publish(claim);
                boolean acknowledged = markPublishedPort.markPublished(
                        claim.id(), workerId, clockPort.now());
                if (!acknowledged) {
                    observability.outbox("lease_lost");
                    LOG.warn("campaign_outbox_ack_lease_lost eventId={} eventType={}",
                            claim.id(), claim.eventType());
                } else {
                    observability.outbox("published");
                    LOG.debug("campaign_outbox_published eventId={} eventType={} traceId={}",
                            claim.id(), claim.eventType(), claim.traceId());
                }
            } catch (RuntimeException exception) {
                observability.outbox("retry");
                boolean recorded;
                try {
                    recorded = failurePort.recordFailure(
                            claim.id(), workerId, clockPort.now(), exception.getMessage(),
                            maxAutomaticAttempts, retryBackoffCap);
                } catch (RuntimeException failureException) {
                    // Keep the row lease-reclaimable when the database is also unavailable.
                    recorded = false;
                    LOG.warn("campaign_outbox_failure_record_deferred eventId={} failureType={}",
                            claim.id(), failureException.getClass().getSimpleName());
                }
                LOG.warn("campaign_outbox_publish_failed eventId={} eventType={} recorded={} failureType={}",
                        claim.id(), claim.eventType(), recorded, exception.getClass().getSimpleName());
            }
        });
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return value;
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return value;
    }

    private static String normalizeWorkerId(String hostName) {
        if (hostName == null || hostName.isBlank()) {
            return "campaign-service";
        }
        return hostName.trim().replaceAll("[^a-zA-Z0-9._-]", "-");
    }
}
