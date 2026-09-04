package com.philia.flashsale.inventory.outbox.adapter.out.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.inventory.outbox.adapter.out.persistence.jpa.entity.OutboxEventJpaEntity;
import com.philia.flashsale.inventory.outbox.adapter.out.persistence.jpa.repository.OutboxEventJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class RegularHoldOutboxDispatchPersistenceAdapterTest {
    private static final Instant NOW = Instant.parse("2026-09-04T13:31:00Z");

    @Test
    void claimsOnlyRegularHoldFactsSoCampaignEventsKeepTheirExistingPublisherOwnership() {
        OutboxEventJpaRepository repository = mock(OutboxEventJpaRepository.class);
        OutboxEventJpaEntity event = event();
        when(repository.findDueForClaim(eq("REGULAR_STOCK_HOLD"), eq(NOW), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(event));
        RegularHoldOutboxDispatchPersistenceAdapter adapter = new RegularHoldOutboxDispatchPersistenceAdapter(repository);

        var claimed = adapter.claimDue("regular-worker", NOW, Duration.ofSeconds(30), 10);

        assertThat(claimed).hasSize(1);
        assertThat(claimed.getFirst().eventId()).isEqualTo(event.getId());
        assertThat(event.getClaimedBy()).isEqualTo("regular-worker");
        assertThat(event.getClaimUntil()).isEqualTo(NOW.plusSeconds(30));
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findDueForClaim(eq("REGULAR_STOCK_HOLD"), eq(NOW), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void appliesConfiguredRetryDelayBeforeTheTerminalFailureBoundary() {
        OutboxEventJpaRepository repository = mock(OutboxEventJpaRepository.class);
        OutboxEventJpaEntity event = event();
        event.claim("regular-worker", NOW.plusSeconds(30));
        when(repository.findById(event.getId())).thenReturn(java.util.Optional.of(event));
        RegularHoldOutboxDispatchPersistenceAdapter adapter = new RegularHoldOutboxDispatchPersistenceAdapter(repository);

        assertThat(adapter.recordFailure(event.getId(), "regular-worker", NOW,
                List.of(Duration.ofSeconds(1), Duration.ofSeconds(3)))).isTrue();

        assertThat(event.getStatus()).isEqualTo("PENDING");
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(event.getClaimedBy()).isNull();
    }

    private static OutboxEventJpaEntity event() {
        UUID id = UUID.randomUUID();
        return new OutboxEventJpaEntity(id, "REGULAR_STOCK_HOLD", UUID.randomUUID(),
                "RegularStockHoldConfirmed", "{}", 1L, 1, UUID.randomUUID().toString(), UUID.randomUUID(),
                UUID.randomUUID(), null, null, NOW);
    }
}
