package com.philia.flashsale.authentication.security.token;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.philia.flashsale.authentication.session.application.login.IssueLoginCredentialsPort;
import com.philia.flashsale.authentication.session.application.refresh.IssueRefreshedCredentialsPort;
import com.philia.flashsale.authentication.configuration.JwtTrustProperties;
import com.philia.flashsale.authentication.account.domain.Account;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(JwtEncoder.class)
/** Nimbus adapter that signs RS256 access tokens with the approved trust claims. */
public class JwtAccessTokenAdapter implements IssueLoginCredentialsPort, IssueRefreshedCredentialsPort {
    private final JwtEncoder encoder;
    private final JwtTrustProperties properties;

    public JwtAccessTokenAdapter(JwtEncoder encoder, JwtTrustProperties properties) {
        this.encoder = encoder; this.properties = properties;
    }

    @Override
    public String issueAccessToken(Account account, UUID sessionId, Instant issuedAt) {
        return issue(account, sessionId, issuedAt);
    }

    @Override
    public String issue(Account account, UUID sessionId, Instant issuedAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(account.id().toString())
                .audience(List.of(properties.audience()))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(900))
                .id(UUID.randomUUID().toString())
                .claim("authorities", account.role().authorities())
                .build();
        JwsHeader headers = JwsHeader.with(SignatureAlgorithm.RS256)
                .type("at+jwt").keyId(properties.keyId()).build();
        return encoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }
}
