package com.philia.flashsale.authentication.session.adapter.in.web;

import com.philia.flashsale.authentication.session.application.logout.LogoutCurrentSessionCommand;
import com.philia.flashsale.authentication.session.application.logout.LogoutSessionUseCase;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "flashsale.authentication.http", name = "enabled", havingValue = "true", matchIfMissing = true)
/** HTTP adapter for idempotent current-session logout and cookie clearing. */
public class LogoutController {
    private final LogoutSessionUseCase useCase;
    private final RefreshCookieWriter cookieWriter;

    public LogoutController(LogoutSessionUseCase useCase, RefreshCookieWriter cookieWriter) {
        this.useCase = useCase; this.cookieWriter = cookieWriter;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        useCase.logoutCurrent(new LogoutCurrentSessionCommand(cookie(request, "refresh_token")));
        cookieWriter.clear(response);
        return ResponseEntity.noContent().build();
    }

    private String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
}
