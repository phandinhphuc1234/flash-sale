package com.philia.flashsale.flashsale.configuration;

import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.IdempotencyCleanupJpaAdapter;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.ReservationExpiryJpaAdapter;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.FlashSaleReservationJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.PurchaseIdempotencyJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.PurchaseEventOutboxJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.PurchaseRequestJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.redis.ReservationExpiryRedisAdapter;
import com.philia.flashsale.flashsale.reservation.adapter.out.redis.ReservationHandoffRedisAdapter;
import com.philia.flashsale.flashsale.reservation.adapter.out.redis.ReservationConfirmationRedisAdapter;
import com.philia.flashsale.flashsale.reservation.adapter.out.redis.ReservationReleaseRedisAdapter;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.ReservationConfirmationJpaAdapter;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.ReservationReleaseJpaAdapter;
import com.philia.flashsale.flashsale.reservation.application.port.in.ConfirmReservationUseCase;
import com.philia.flashsale.flashsale.reservation.application.port.in.ReleaseReservationUseCase;
import com.philia.flashsale.flashsale.reservation.application.usecase.ConfirmReservationService;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReleaseReservationService;
import com.philia.flashsale.flashsale.reservation.application.port.out.PersistAcceptedPurchasePort;
import com.philia.flashsale.flashsale.reservation.adapter.in.messaging.redis.ReservationHandoffConsumerGroupInitializer;
import com.philia.flashsale.flashsale.reservation.adapter.in.messaging.redis.ReservationHandoffStreamConsumer;
import com.philia.flashsale.flashsale.reservation.application.usecase.ExpireReservationsService;
import com.philia.flashsale.flashsale.reservation.application.usecase.IdempotencyCleanupService;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReservationAcceptanceFlow;
import com.philia.flashsale.flashsale.reservation.domain.policy.ReservationExpiryPolicy;
import com.philia.flashsale.flashsale.observability.FlashSaleObservability;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Composition root for durable-first reservation expiry and retention cleanup. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@AutoConfigureAfter(RedisAutoConfiguration.class)
public class ReservationDurabilityConfiguration {
    @Bean
    @ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
    ReservationExpiryRedisAdapter reservationExpiryRedisAdapter(StringRedisTemplate redis,
            FlashSaleObservability observability) {
        return new ReservationExpiryRedisAdapter(redis, observability);
    }

    @Bean
    ReservationExpiryPolicy reservationExpiryPolicy() {
        return new ReservationExpiryPolicy();
    }

    @Bean
    @ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnBean({ReservationExpiryJpaAdapter.class, ReservationExpiryRedisAdapter.class,
            FlashSaleReservationJpaRepository.class, PurchaseRequestJpaRepository.class})
    ExpireReservationsService expireReservationsService(ReservationExpiryJpaAdapter persistence,
            ReservationExpiryRedisAdapter redis, ReservationExpiryPolicy policy) {
        return new ExpireReservationsService(persistence, persistence, redis, policy);
    }

    @Bean
    @ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnBean({IdempotencyCleanupJpaAdapter.class, PurchaseIdempotencyJpaRepository.class})
    IdempotencyCleanupService idempotencyCleanupService(IdempotencyCleanupJpaAdapter persistence) {
        return new IdempotencyCleanupService(persistence);
    }

    @Bean
    @ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnBean({PersistAcceptedPurchasePort.class, ReservationHandoffRedisAdapter.class,
            PurchaseRequestJpaRepository.class, FlashSaleReservationJpaRepository.class,
            PurchaseIdempotencyJpaRepository.class, PurchaseEventOutboxJpaRepository.class})
    ReservationAcceptanceFlow reservationAcceptanceFlow(PersistAcceptedPurchasePort durableAcceptance,
            ReservationHandoffRedisAdapter handoff) {
        return new ReservationAcceptanceFlow(durableAcceptance, handoff);
    }

    @Bean
    @ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnBean({ReservationHandoffConsumerGroupInitializer.class,
            ReservationHandoffRedisAdapter.class, ReservationAcceptanceFlow.class,
            PersistAcceptedPurchasePort.class})
    ReservationHandoffStreamConsumer reservationHandoffStreamConsumer(
            ReservationHandoffConsumerGroupInitializer initializer,
            ReservationHandoffRedisAdapter handoff,
            ReservationAcceptanceFlow acceptance,
            PersistAcceptedPurchasePort durableAcceptance) {
        return new ReservationHandoffStreamConsumer(initializer, handoff, acceptance, durableAcceptance);
    }

    @Bean
    @ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnBean({ReservationConfirmationJpaAdapter.class, ReservationConfirmationRedisAdapter.class})
    ConfirmReservationUseCase confirmReservationUseCase(ReservationConfirmationJpaAdapter persistence,
            ReservationConfirmationRedisAdapter redis) {
        return new ConfirmReservationService(persistence, redis);
    }

    @Bean
    @ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnBean({ReservationReleaseJpaAdapter.class, ReservationReleaseRedisAdapter.class})
    ReleaseReservationUseCase releaseReservationUseCase(ReservationReleaseJpaAdapter persistence,
            ReservationReleaseRedisAdapter redis) {
        return new ReleaseReservationService(persistence, redis);
    }
}
