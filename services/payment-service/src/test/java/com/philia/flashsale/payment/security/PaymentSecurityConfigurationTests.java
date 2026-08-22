package com.philia.flashsale.payment.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Guards the disabled-feature security composition used by cloud health probes. */
class PaymentSecurityConfigurationTests {

    @Test
    void keepsProbeChainActiveWhenPaymentAcceptanceIsDisabled() throws Exception {
        assertThat(PaymentSecurityConfiguration.class
                .isAnnotationPresent(ConditionalOnProperty.class)).isFalse();

        Method apiChain = PaymentSecurityConfiguration.class
                .getDeclaredMethod("paymentApiSecurityChain", org.springframework.security.config.annotation.web.builders.HttpSecurity.class,
                        org.springframework.security.oauth2.jwt.JwtDecoder.class,
                        org.springframework.core.convert.converter.Converter.class,
                        com.philia.flashsale.payment.websupport.error.PaymentAuthenticationEntryPoint.class,
                        com.philia.flashsale.payment.websupport.error.PaymentAccessDeniedHandler.class);
        assertThat(apiChain.getAnnotation(ConditionalOnProperty.class)).isNotNull();
        assertThat(apiChain.getAnnotation(ConditionalOnProperty.class).havingValue()).isEqualTo("true");

        Method defaultChain = PaymentSecurityConfiguration.class
                .getDeclaredMethod("paymentDenyByDefaultSecurityChain",
                        org.springframework.security.config.annotation.web.builders.HttpSecurity.class);
        assertThat(defaultChain.getAnnotation(ConditionalOnProperty.class)).isNull();
    }
}
