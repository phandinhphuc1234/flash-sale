package com.philia.flashsale.authentication.session.adapter.in.web;

import java.util.UUID;

import com.philia.flashsale.authentication.session.application.logout.LogoutAllSessionsCommand;
import com.philia.flashsale.authentication.session.application.logout.LogoutSessionUseCase;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "flashsale.authentication.http", name = "enabled", havingValue = "true", matchIfMissing = true)
/** HTTP adapter that derives logout-all ownership only from a verified JWT subject. */
public class LogoutAllController {
    private final LogoutSessionUseCase useCase;
    private final RefreshCookieWriter cookieWriter;

    public LogoutAllController(LogoutSessionUseCase useCase, RefreshCookieWriter cookieWriter) {
        this.useCase = useCase; this.cookieWriter = cookieWriter;
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal Jwt jwt, HttpServletResponse response) {
        useCase.logoutAll(new LogoutAllSessionsCommand(UUID.fromString(jwt.getSubject())));
        cookieWriter.clear(response);
        return ResponseEntity.noContent().build();
    }
}
