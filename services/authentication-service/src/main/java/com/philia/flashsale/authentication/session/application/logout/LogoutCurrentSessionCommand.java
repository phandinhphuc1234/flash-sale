package com.philia.flashsale.authentication.session.application.logout;

/** Current-session logout input containing the raw cookie only at the application edge. */
public record LogoutCurrentSessionCommand(String rawCredential) { }
