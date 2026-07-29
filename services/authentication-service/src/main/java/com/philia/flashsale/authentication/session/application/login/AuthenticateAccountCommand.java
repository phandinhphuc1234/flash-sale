package com.philia.flashsale.authentication.session.application.login;

/** Framework-free login input including bounded caller metadata captured at the HTTP edge. */
public record AuthenticateAccountCommand(String login, String password, String deviceName,
                                         String userAgent, String directIp) { }
