package com.philia.flashsale.product.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.Test;

import com.philia.flashsale.product.application.command.CreateProductDraftCommand;
import com.philia.flashsale.product.application.exception.DuplicateProductCodeException;
import com.philia.flashsale.product.application.exception.IdempotencyKeyReusedException;
import com.philia.flashsale.product.application.port.in.CreateProductDraftUseCase;
import com.philia.flashsale.product.application.port.out.AdminIdempotencyPort;
import com.philia.flashsale.product.application.port.out.CheckCatalogUniquenessPort;
import com.philia.flashsale.product.application.port.out.RecordCatalogAdminAuditPort;
import com.philia.flashsale.product.application.port.out.SaveAdminProductPort;
import com.philia.flashsale.product.application.result.CreateDraftIdempotencyDecision;
import com.philia.flashsale.product.application.result.CreateProductDraftResult;
import com.philia.flashsale.product.domain.model.AdminCommandName;
import com.philia.flashsale.product.domain.model.CatalogAdminActor;
import com.philia.flashsale.product.domain.model.ProductAggregate;
import com.philia.flashsale.product.domain.model.TraceId;

class CreateProductDraftUseCaseTests {

    @Test
    void repeatedCreateWithSameIdempotencyKeyReplaysOriginalResult() {
        FakeSave save = new FakeSave();
        CreateProductDraftService service = service(new FakeUniqueness(), save, new FakeIdempotency());
        CreateProductDraftCommand command = command("PROD-001", "product-001", "idem-1");

        CreateProductDraftResult first = service.createDraft(command);
        CreateProductDraftResult second = service.createDraft(command);

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(save.savedCount()).isEqualTo(1);
    }

    @Test
    void concurrentSerializedRequestsWithSameKeyCreateOnlyOneDraft() throws Exception {
        FakeSave save = new FakeSave();
        CreateProductDraftService service = service(new FakeUniqueness(), save, new FakeIdempotency());
        CreateProductDraftCommand command = command("PROD-001", "product-001", "idem-1");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<CreateProductDraftResult>> futures = List.of(
                    executor.submit(() -> invokeTogether(service, command, ready, start)),
                    executor.submit(() -> invokeTogether(service, command, ready, start)));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<CreateProductDraftResult> results = List.of(
                    futures.get(0).get(5, TimeUnit.SECONDS),
                    futures.get(1).get(5, TimeUnit.SECONDS));

            assertThat(results).extracting(CreateProductDraftResult::id).containsOnly(results.get(0).id());
            assertThat(results).extracting(CreateProductDraftResult::replayed)
                    .containsExactlyInAnyOrder(false, true);
            assertThat(save.savedCount()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void expiredKeyStartsFreshCommandAndDoesNotReplayOldOutcome() {
        FakeSave save = new FakeSave();
        FakeIdempotency idempotency = new FakeIdempotency();
        CreateProductDraftService service = service(new FakeUniqueness(), save, idempotency);

        CreateProductDraftResult first = service.createDraft(command("PROD-001", "product-001", "idem-1"));
        idempotency.advance(Duration.ofDays(7));
        CreateProductDraftResult fresh = service.createDraft(command("PROD-002", "product-002", "idem-1"));

        assertThat(fresh.replayed()).isFalse();
        assertThat(fresh.id()).isNotEqualTo(first.id());
        assertThat(save.savedCount()).isEqualTo(2);
    }

    @Test
    void activeKeyWithDifferentRequestIsRejected() {
        CreateProductDraftService service = service(
                new FakeUniqueness(),
                new FakeSave(),
                new FakeIdempotency());
        service.createDraft(command("PROD-001", "product-001", "idem-1"));

        assertThatThrownBy(() -> service.createDraft(command("PROD-002", "product-002", "idem-1")))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void duplicateCodeIsRejectedBeforeSave() {
        FakeUniqueness uniqueness = new FakeUniqueness();
        uniqueness.duplicateCode = true;
        FakeSave save = new FakeSave();
        CreateProductDraftService service = service(uniqueness, save, new FakeIdempotency());

        assertThatThrownBy(() -> service.createDraft(command("PROD-001", "product-001", "idem-1")))
                .isInstanceOf(DuplicateProductCodeException.class);
        assertThat(save.savedCount()).isZero();
    }

    @Test
    void auditedDecoratorRecordsReplayAfterDelegateReturns() {
        CapturingAudit audit = new CapturingAudit();
        UUID productId = UUID.randomUUID();
        CreateProductDraftUseCase delegate = command -> new CreateProductDraftResult(
                productId,
                com.philia.flashsale.product.domain.model.ProductStatus.DRAFT,
                0,
                true);
        AuditedCreateProductDraftUseCase audited = new AuditedCreateProductDraftUseCase(delegate, audit);

        audited.createDraft(command("PROD-001", "product-001", "idem-1"));

        assertThat(audit.entries).containsExactly(new AuditEntry(
                "admin-1",
                "trace-1",
                productId,
                "REPLAYED",
                null,
                0L));
    }

    @Test
    void auditedDecoratorRecordsDuplicateConflictThenRethrows() {
        CapturingAudit audit = new CapturingAudit();
        CreateProductDraftUseCase delegate = command -> {
            throw new DuplicateProductCodeException(command.code());
        };
        AuditedCreateProductDraftUseCase audited = new AuditedCreateProductDraftUseCase(delegate, audit);

        assertThatThrownBy(() -> audited.createDraft(command("PROD-001", "product-001", "idem-1")))
                .isInstanceOf(DuplicateProductCodeException.class);
        assertThat(audit.entries).containsExactly(new AuditEntry(
                "admin-1",
                "trace-1",
                null,
                "CONFLICT",
                "DUPLICATE_PRODUCT_CODE",
                null));
    }

    private static CreateProductDraftResult invokeTogether(
            CreateProductDraftService service,
            CreateProductDraftCommand command,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test did not start in time");
        }
        return service.createDraft(command);
    }

    private static CreateProductDraftService service(
            CheckCatalogUniquenessPort uniqueness,
            SaveAdminProductPort save,
            AdminIdempotencyPort idempotency) {
        return new CreateProductDraftService(uniqueness, save, idempotency, new NoopAudit());
    }

    private static CreateProductDraftCommand command(String code, String slug, String key) {
        return new CreateProductDraftCommand(
                new CatalogAdminActor("admin-1"),
                new TraceId("trace-1"),
                key,
                code,
                slug,
                "Product 001",
                "Short",
                "Long");
    }

    private static final class FakeUniqueness implements CheckCatalogUniquenessPort {
        private boolean duplicateCode;

        @Override
        public boolean productCodeExists(String code) {
            return duplicateCode;
        }

        @Override
        public boolean productSlugExists(String slug) {
            return false;
        }

        @Override
        public boolean variantSkuExists(String sku) {
            return false;
        }

        @Override
        public boolean barcodeExists(String barcode) {
            return false;
        }
    }

    private static final class FakeSave implements SaveAdminProductPort {
        private final AtomicInteger savedCount = new AtomicInteger();

        @Override
        public ProductAggregate saveDraft(ProductAggregate product) {
            savedCount.incrementAndGet();
            return product;
        }

        int savedCount() {
            return savedCount.get();
        }
    }

    /**
     * Mimics the adapter contract: a fresh decision retains per-key serialization until outcome storage.
     */
    private static final class FakeIdempotency implements AdminIdempotencyPort {
        private final Map<String, Stored> stored = new ConcurrentHashMap<>();
        private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
        private volatile Instant now = Instant.parse("2026-07-20T00:00:00Z");

        @Override
        public CreateDraftIdempotencyDecision resolveCreateDraft(
                String actorId,
                String idempotencyKey,
                String requestHash) {
            String key = actorId + ":" + idempotencyKey;
            ReentrantLock lock = locks.computeIfAbsent(key, ignored -> new ReentrantLock());
            lock.lock();
            Stored value = stored.get(key);
            if (value != null && !value.expiresAt().isAfter(now)) {
                stored.remove(key);
                value = null;
            }
            if (value == null) {
                return CreateDraftIdempotencyDecision.fresh();
            }
            try {
                return value.requestHash().equals(requestHash)
                        ? CreateDraftIdempotencyDecision.replay(value.result())
                        : CreateDraftIdempotencyDecision.conflict();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void storeCreateDraftOutcome(
                String actorId,
                String idempotencyKey,
                AdminCommandName commandName,
                String requestHash,
                UUID productId,
                CreateProductDraftResult result) {
            String key = actorId + ":" + idempotencyKey;
            ReentrantLock lock = locks.get(key);
            try {
                stored.put(key, new Stored(requestHash, result, now.plus(Duration.ofDays(7))));
            } finally {
                lock.unlock();
            }
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        private record Stored(
                String requestHash,
                CreateProductDraftResult result,
                Instant expiresAt) {
        }
    }

    private static final class NoopAudit implements RecordCatalogAdminAuditPort {
        @Override
        public void record(
                String actorId,
                String traceId,
                AdminCommandName commandName,
                UUID targetProductId,
                String outcome,
                String errorCode,
                Long productVersion) {
        }
    }

    private static final class CapturingAudit implements RecordCatalogAdminAuditPort {
        private final java.util.ArrayList<AuditEntry> entries = new java.util.ArrayList<>();

        @Override
        public void record(
                String actorId,
                String traceId,
                AdminCommandName commandName,
                UUID targetProductId,
                String outcome,
                String errorCode,
                Long productVersion) {
            entries.add(new AuditEntry(
                    actorId,
                    traceId,
                    targetProductId,
                    outcome,
                    errorCode,
                    productVersion));
        }
    }

    private record AuditEntry(
            String actorId,
            String traceId,
            UUID targetProductId,
            String outcome,
            String errorCode,
            Long productVersion) {
    }
}
