package com.philia.flashsale.payment.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** Direct validator checks for issuer-independent type/audience/subject requirements. */
class PaymentJwtTrustConfigurationTests {
    @Test
    void acceptsExpectedTypeAudienceAndSubject() {
        Jwt jwt = Jwt.withTokenValue("token").header("typ", "at+jwt")
                .subject("22222222-2222-2222-2222-222222222222")
                .audience(List.of("flash-sale-api"))
                .issuedAt(Instant.now().minusSeconds(1)).expiresAt(Instant.now().plusSeconds(60)).build();

        assertThat(new PaymentJwtTrustConfiguration.TypeValidator("at+jwt").validate(jwt).hasErrors()).isFalse();
        assertThat(new PaymentJwtTrustConfiguration.AudienceValidator("flash-sale-api").validate(jwt).hasErrors()).isFalse();
        assertThat(new PaymentJwtTrustConfiguration.SubjectValidator().validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void rejectsWrongTypeAndMissingSubject() {
        Jwt jwt = Jwt.withTokenValue("token").header("typ", "JWT")
                .audience(List.of("other-api")).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

        assertThat(new PaymentJwtTrustConfiguration.TypeValidator("at+jwt").validate(jwt).hasErrors()).isTrue();
        assertThat(new PaymentJwtTrustConfiguration.AudienceValidator("flash-sale-api").validate(jwt).hasErrors()).isTrue();
        assertThat(new PaymentJwtTrustConfiguration.SubjectValidator().validate(jwt).hasErrors()).isTrue();
    }
}
