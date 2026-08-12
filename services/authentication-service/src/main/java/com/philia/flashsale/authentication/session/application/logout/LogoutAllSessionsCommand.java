package com.philia.flashsale.authentication.session.application.logout;

import java.util.UUID;
/** Logout-all input derived from a verified JWT subject, never from an arbitrary request field. */
public record LogoutAllSessionsCommand(UUID userId) { }
