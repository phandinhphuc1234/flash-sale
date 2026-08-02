package com.philia.flashsale.authentication.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flashsale.auth.service-token")
public record ServiceTokenProperties(String audience, int maxTtlSeconds) { }
