package com.philia.flashsale.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GatewaySecurityConfigurationTests {

    @Test
    void deniesDocumentationWhenTheOptInIsDisabled() {
        var decision = GatewaySecurityConfiguration.documentationAccess(false).block();

        assertThat(decision).isNotNull();
        assertThat(decision.isGranted()).isFalse();
    }

    @Test
    void permitsDocumentationWhenTheLocalOptInIsEnabled() {
        var decision = GatewaySecurityConfiguration.documentationAccess(true).block();

        assertThat(decision).isNotNull();
        assertThat(decision.isGranted()).isTrue();
    }
}
