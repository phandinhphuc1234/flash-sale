package com.philia.flashsale.flashsale.configuration;

import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.IdempotencyCleanupJpaAdapter;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.ReservationExpiryJpaAdapter;
import com.philia.flashsale.flashsale.reservation.adapter.out.redis.ReservationExpiryRedisAdapter;
import com.philia.flashsale.flashsale.reservation.adapter.out.redis.ReservationHandoffRedisAdapter;
import com.philia.flashsale.flashsale.reservation.application.port.out.PersistAcceptedPurchasePort;
import com.philia.flashsale.flashsale.reservation.adapter.in.messaging.redis.ReservationHandoffConsumerGroupInitializer;
import com.philia.flashsale.flashsale.reservation.adapter.in.messaging.redis.ReservationHandoffStreamConsumer;
import com.philia.flashsale.flashsale.reservation.application.usecase.ExpireReservationsService;
import com.philia.flashsale.flashsale.reservation.application.usecase.IdempotencyCleanupService;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReservationAcceptanceFlow;
import com.philia.flashsale.flashsale.reservation.domain.policy.ReservationExpiryPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Composition root for durable-first reservation expiry and retention cleanup. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(StringRedisTemplate.class)
public class ReservationDurabilityConfiguration {
    @Bean
    ReservationExpiryRedisAdapter reservationExpiryRedisAdapter(StringRedisTemplate redis) {
        return new ReservationExpiryRedisAdapter(redis);
    }

    @Bean
    ReservationExpiryPolicy reservationExpiryPolicy() {
        return new ReservationExpiryPolicy();
    }

    @Bean
    ExpireReservationsService expireReservationsService(ReservationExpiryJpaAdapter persistence,
            ReservationExpiryRedisAdapter redis, ReservationExpiryPolicy policy) {
        return new ExpireReservationsService(persistence, persistence, redis, policy);
    }

    @Bean
    IdempotencyCleanupService idempotencyCleanupService(IdempotencyCleanupJpaAdapter persistence) {
        return new IdempotencyCleanupService(persistence);
    }

    @Bean
    ReservationAcceptanceFlow reservationAcceptanceFlow(PersistAcceptedPurchasePort durableAcceptance,
            ReservationHandoffRedisAdapter handoff) {
        return new ReservationAcceptanceFlow(durableAcceptance, handoff);
    }

    @Bean
    ReservationHandoffStreamConsumer reservationHandoffStreamConsumer(
            ReservationHandoffConsumerGroupInitializer initializer,
            ReservationHandoffRedisAdapter handoff,
            ReservationAcceptanceFlow acceptance,
            PersistAcceptedPurchasePort durableAcceptance) {
        return new ReservationHandoffStreamConsumer(initializer, handoff, acceptance, durableAcceptance);
    }
}
