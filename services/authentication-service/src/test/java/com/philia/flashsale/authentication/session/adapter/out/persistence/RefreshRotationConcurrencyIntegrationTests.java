package com.philia.flashsale.authentication.session.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.philia.flashsale.authentication.session.application.refresh.RotateRefreshCredentialPort;
import com.philia.flashsale.authentication.session.domain.LoginSessionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.dao.DataIntegrityViolationException;

class RefreshRotationConcurrencyIntegrationTests extends AuthenticationPostgreSqlIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");

    @Test
    @Timeout(30)
    void concurrentUseOfOneCredentialAllowsOneRotationThenCommitsReuseCompromise() throws Exception {
        SessionFixture fixture = createCurrentSession(hash('a'), NOW);

        List<Object> outcomes = invokeConcurrently(
                () -> persistenceAdapter.rotate(fixture.tokenHash(), hash('b'), NOW),
                () -> persistenceAdapter.rotate(fixture.tokenHash(), hash('c'), NOW));

        assertThat(outcomes)
                .filteredOn(RotateRefreshCredentialPort.RefreshRotation.class::isInstance)
                .hasSize(1);
        assertThat(outcomes)
                .filteredOn(RotateRefreshCredentialPort.RefreshReuseDetected.class::isInstance)
                .hasSize(1);

        LoginSessionJpaEntity persistedSession = sessions.findById(fixture.sessionId()).orElseThrow();
        List<RefreshCredentialJpaEntity> chain = refreshCredentials.findAllBySessionId(fixture.sessionId());
        assertThat(persistedSession.status()).isEqualTo(LoginSessionStatus.COMPROMISED);
        assertThat(persistedSession.revokedAt()).isNotNull();
        assertThat(chain).hasSize(2).allMatch(token -> token.revokedAt() != null);
        assertThat(chain).noneMatch(token -> token.usedAt() == null && token.revokedAt() == null);
    }

    @Test
    void successfulRotationRetiresTheOldRowBeforeKeepingOneCurrentSuccessor() {
        SessionFixture fixture = createCurrentSession(hash('d'), NOW);

        RotateRefreshCredentialPort.RotationOutcome outcome =
                persistenceAdapter.rotate(fixture.tokenHash(), hash('e'), NOW);

        assertThat(outcome).isInstanceOf(RotateRefreshCredentialPort.RefreshRotation.class);
        List<RefreshCredentialJpaEntity> chain = refreshCredentials.findAllBySessionId(fixture.sessionId());
        assertThat(chain).hasSize(2);
        RefreshCredentialJpaEntity retired = chain.stream()
                .filter(token -> token.id().equals(fixture.refreshId()))
                .findFirst()
                .orElseThrow();
        RefreshCredentialJpaEntity successor = chain.stream()
                .filter(token -> !token.id().equals(fixture.refreshId()))
                .findFirst()
                .orElseThrow();
        assertThat(retired.usedAt()).isEqualTo(NOW);
        assertThat(retired.replacedByTokenId()).isEqualTo(successor.id());
        assertThat(successor.parentTokenId()).isEqualTo(retired.id());
        assertThat(chain).filteredOn(token -> token.usedAt() == null && token.revokedAt() == null).hasSize(1);
    }

    @Test
    void partialUniqueIndexRejectsTwoCurrentCredentialsForOneSession() {
        SessionFixture fixture = createCurrentSession(hash('f'), NOW);
        RefreshCredentialJpaEntity competingCurrent = new RefreshCredentialJpaEntity(
                UUID.randomUUID(),
                fixture.sessionId(),
                hash('1'),
                null,
                null,
                NOW,
                NOW.plusSeconds(3600),
                null,
                null,
                null);

        assertThatThrownBy(() -> refreshCredentials.saveAndFlush(competingCurrent))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(refreshCredentials.findAllBySessionId(fixture.sessionId())).hasSize(1);
    }

    private List<Object> invokeConcurrently(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = List.of(
                    executor.submit(() -> invokeTogether(first, ready, start)),
                    executor.submit(() -> invokeTogether(second, ready, start)));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return futures.stream().map(this::futureOutcome).toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private Object invokeTogether(Callable<?> action, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent refresh test did not start in time");
        }
        return action.call();
    }

    private Object futureOutcome(Future<?> future) {
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            return exception.getCause();
        } catch (Exception exception) {
            throw new IllegalStateException("Concurrent refresh operation did not finish", exception);
        }
    }
}
