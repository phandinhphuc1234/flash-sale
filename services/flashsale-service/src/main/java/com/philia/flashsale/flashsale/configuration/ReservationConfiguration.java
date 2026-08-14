package com.philia.flashsale.flashsale.configuration;

import com.philia.flashsale.flashsale.reservation.adapter.out.redis.AtomicReservationRedisAdapter;
import com.philia.flashsale.flashsale.reservation.application.port.in.ReserveCampaignQuotaUseCase;
import com.philia.flashsale.flashsale.reservation.application.port.out.LoadCampaignEndPort;
import com.philia.flashsale.flashsale.reservation.application.port.out.PersistAcceptedPurchasePort;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReservationFingerprintService;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReservationIdentityFactory;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReservationSubmissionService;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReserveCampaignQuotaService;
import com.philia.flashsale.flashsale.observability.FlashSaleObservability;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Composition root for the public admission and durable acceptance path. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@AutoConfigureAfter(RedisAutoConfiguration.class)
public class ReservationConfiguration {

    @Bean
    @ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
    AtomicReservationRedisAdapter atomicReservationRedisAdapter(StringRedisTemplate redis,
            FlashSaleObservability observability) {
        return new AtomicReservationRedisAdapter(redis, observability);
    }

    @Bean
    ReservationFingerprintService reservationFingerprintService() {
        return new ReservationFingerprintService();
    }

    @Bean
    ReservationIdentityFactory reservationIdentityFactory() {
        return new ReservationIdentityFactory();
    }

    @Bean
    Clock flashSaleClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
    ReserveCampaignQuotaUseCase reserveCampaignQuotaUseCase(
            AtomicReservationRedisAdapter atomicReservation,
            ReservationFingerprintService fingerprints,
            ReservationIdentityFactory identities,
            Clock clock,
            FlashSaleProperties properties) {
        return new ReserveCampaignQuotaService(atomicReservation, fingerprints, identities, clock,
                properties.ttl(), properties.idempotencyRetentionAfterCampaignEnd());
    }

    @Bean
    @ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnBean(PersistAcceptedPurchasePort.class)
        ReservationSubmissionService reservationSubmissionService(
            ReserveCampaignQuotaUseCase admission,
            LoadCampaignEndPort campaignEnds,
            PersistAcceptedPurchasePort durableAcceptance,
            Clock clock) {
        return new ReservationSubmissionService(admission, campaignEnds, durableAcceptance, clock);
    }
}
