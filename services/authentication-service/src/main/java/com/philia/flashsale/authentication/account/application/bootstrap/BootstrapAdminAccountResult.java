package com.philia.flashsale.authentication.account.application.bootstrap;

import java.util.UUID;

/** Sanitized bootstrap outcome; it never contains a password, hash, or token. */
public record BootstrapAdminAccountResult(UUID accountId, boolean created) { }
