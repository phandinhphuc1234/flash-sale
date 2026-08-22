package com.philia.flashsale.authentication.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime inputs for the disabled-by-default, one-time administrator bootstrap Job. */
@ConfigurationProperties(prefix = "flashsale.authentication.admin-bootstrap")
public record AdminBootstrapProperties(boolean enabled, String email, String username, String password) { }
