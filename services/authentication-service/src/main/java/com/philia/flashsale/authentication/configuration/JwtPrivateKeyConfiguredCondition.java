package com.philia.flashsale.authentication.configuration;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/** Enables JWT signing only when a private key is actually supplied, not merely declared as an empty placeholder. */
final class JwtPrivateKeyConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var environment = context.getEnvironment();
        return StringUtils.hasText(environment.getProperty("flashsale.auth.jwt.private-key-pem"))
                || StringUtils.hasText(environment.getProperty("flashsale.auth.jwt.private-key-location"));
    }
}
