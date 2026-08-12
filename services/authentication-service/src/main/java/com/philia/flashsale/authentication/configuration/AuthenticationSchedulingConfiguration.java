package com.philia.flashsale.authentication.configuration;

import java.time.Clock;
import java.time.Duration;

import com.philia.flashsale.authentication.cleanup.application.CleanupExpiredSessionsService;
import com.philia.flashsale.authentication.cleanup.application.CleanupExpiredSessionsUseCase;
import com.philia.flashsale.authentication.cleanup.application.PurgeExpiredAuthenticationStatePort;
import com.philia.flashsale.authentication.account.adapter.out.persistence.AccountJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnBean(AccountJpaRepository.class)
/** Enables the scheduled inbound adapter and wires it only when cleanup persistence exists. */
public class AuthenticationSchedulingConfiguration {

    @Bean
    @ConditionalOnBean(PurgeExpiredAuthenticationStatePort.class)
    CleanupExpiredSessionsUseCase cleanupExpiredSessionsUseCase(
            PurgeExpiredAuthenticationStatePort purgePort, Clock clock, AuthenticationProperties properties) {
        int days = properties.retention() == null ? 30 : properties.retention().days();
        if (days <= 0) {
            throw new IllegalArgumentException("Authentication retention days must be positive");
        }
        return new CleanupExpiredSessionsService(purgePort, clock, Duration.ofDays(days));
    }
}
