package com.philia.flashsale.campaign.outbox.adapter.in.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.campaign.campaign.application.port.out.CampaignClockPort;
import com.philia.flashsale.campaign.outbox.application.model.OutboxClaim;
import com.philia.flashsale.campaign.outbox.application.port.out.ClaimDueCampaignOutboxEventsPort;
import com.philia.flashsale.campaign.outbox.application.port.out.MarkCampaignOutboxPublishedPort;
import com.philia.flashsale.campaign.outbox.application.port.out.PublishCampaignOutboxEventPort;
import com.philia.flashsale.campaign.outbox.application.port.out.RecordCampaignOutboxFailurePort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** Verifies B6's lease-aware relay, retry boundary, and trace-context restoration. */
class CampaignOutboxPublisherJobTests {

    private static final Instant NOW = Instant.parse("2030-08-01T10:00:00Z");

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void publishesEveryClaimAndAcknowledgesOnlyAfterPublisherReturns() {
        Fixtures fixtures = new Fixtures();
        OutboxClaim first = claim("Scheduled");
        OutboxClaim second = claim("Activated");
        when(fixtures.claimPort.claimDue(anyString(), eq(NOW), eq(Duration.ofSeconds(30)), eq(100)))
                .thenReturn(List.of(first, second));
        when(fixtures.clock.now()).thenReturn(NOW);

        fixtures.job().publishDue();

        verify(fixtures.publisher).publish(first);
        verify(fixtures.publisher).publish(second);
        verify(fixtures.markPublished).markPublished(eq(first.id()), anyString(), eq(NOW));
        verify(fixtures.markPublished).markPublished(eq(second.id()), anyString(), eq(NOW));
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void recordsFailureAndContinuesWithTheRestOfTheBatch() {
        Fixtures fixtures = new Fixtures();
        OutboxClaim failed = claim("Scheduled");
        OutboxClaim succeeding = claim("Activated");
        when(fixtures.claimPort.claimDue(anyString(), eq(NOW), eq(Duration.ofSeconds(30)), eq(100)))
                .thenReturn(List.of(failed, succeeding));
        when(fixtures.clock.now()).thenReturn(NOW);
        doThrow(new IllegalStateException("broker unavailable"))
                .when(fixtures.publisher).publish(failed);

        fixtures.job().publishDue();

        verify(fixtures.failurePort).recordFailure(
                eq(failed.id()), anyString(), eq(NOW), eq("broker unavailable"), eq(10), eq(Duration.ofSeconds(60)));
        verify(fixtures.publisher).publish(succeeding);
        verify(fixtures.markPublished).markPublished(eq(succeeding.id()), anyString(), eq(NOW));
    }

    @Test
    void restoresExistingTraceContextAfterEachClaim() {
        Fixtures fixtures = new Fixtures();
        OutboxClaim event = claim("Scheduled");
        when(fixtures.claimPort.claimDue(anyString(), eq(NOW), eq(Duration.ofSeconds(30)), eq(100)))
                .thenReturn(List.of(event));
        when(fixtures.clock.now()).thenReturn(NOW);
        MDC.put("traceId", "parent-trace");

        fixtures.job().publishDue();

        assertThat(MDC.get("traceId")).isEqualTo("parent-trace");
    }

    @Test
    void treatsClaimDatabaseFailureAsADeferredScan() {
        Fixtures fixtures = new Fixtures();
        when(fixtures.clock.now()).thenReturn(NOW);
        when(fixtures.claimPort.claimDue(anyString(), eq(NOW), eq(Duration.ofSeconds(30)), eq(100)))
                .thenThrow(new IllegalStateException("database unavailable"));

        fixtures.job().publishDue();

        verify(fixtures.publisher, never()).publish(any());
        verify(fixtures.markPublished, never()).markPublished(any(), anyString(), any());
    }

    @Test
    void rejectsInvalidOperationalSettings() {
        Fixtures fixtures = new Fixtures();

        assertThatThrownBy(() -> new CampaignOutboxPublisherJob(
                fixtures.claimPort, fixtures.publisher, fixtures.markPublished, fixtures.failurePort,
                fixtures.clock, Duration.ZERO, 100, 10, Duration.ofSeconds(60), "campaign"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claim lease");
    }

    private OutboxClaim claim(String type) {
        return new OutboxClaim(
                UUID.randomUUID(), UUID.randomUUID(), 1, "Campaign" + type, 1,
                UUID.randomUUID().toString(), "{}", "trace-" + type,
                NOW, NOW.plusSeconds(30));
    }

    private static final class Fixtures {
        private final ClaimDueCampaignOutboxEventsPort claimPort = mock(ClaimDueCampaignOutboxEventsPort.class);
        private final PublishCampaignOutboxEventPort publisher = mock(PublishCampaignOutboxEventPort.class);
        private final MarkCampaignOutboxPublishedPort markPublished = mock(MarkCampaignOutboxPublishedPort.class);
        private final RecordCampaignOutboxFailurePort failurePort = mock(RecordCampaignOutboxFailurePort.class);
        private final CampaignClockPort clock = mock(CampaignClockPort.class);

        private CampaignOutboxPublisherJob job() {
            return new CampaignOutboxPublisherJob(
                    claimPort, publisher, markPublished, failurePort, clock,
                    Duration.ofSeconds(30), 100, 10, Duration.ofSeconds(60), "campaign-test");
        }
    }
}
