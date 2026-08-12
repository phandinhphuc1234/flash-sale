package com.philia.flashsale.authentication.session.application.refresh;

/** Framework-free refresh input carrying the raw cookie value only at the application boundary. */
public record RefreshSessionCommand(String rawCredential) { }
