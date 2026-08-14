package com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa;

import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.PurchaseIdempotencyJpaRepository;
import com.philia.flashsale.flashsale.reservation.application.port.out.DeleteExpiredIdempotencyPort;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyCleanupJpaAdapter implements DeleteExpiredIdempotencyPort {
    private final PurchaseIdempotencyJpaRepository records;

    public IdempotencyCleanupJpaAdapter(PurchaseIdempotencyJpaRepository records) {
        this.records = Objects.requireNonNull(records, "records");
    }

    @Override
    @Transactional
    public int deleteExpired(Instant now, int batchSize) {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("batchSize must be in 1..100");
        }
        var expired = records.findTop100ByRetainedUntilLessThanEqualOrderByRetainedUntilAsc(now);
        int deleted = Math.min(batchSize, expired.size());
        records.deleteAll(expired.subList(0, deleted));
        return deleted;
    }
}
