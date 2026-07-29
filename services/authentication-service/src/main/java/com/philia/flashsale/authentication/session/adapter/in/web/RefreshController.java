package com.philia.flashsale.authentication.session.adapter.in.web;

import com.philia.flashsale.authentication.session.application.refresh.RefreshSessionCommand;
import com.philia.flashsale.authentication.session.application.refresh.RefreshSessionResult;
import com.philia.flashsale.authentication.session.application.refresh.RefreshSessionUseCase;
import com.philia.flashsale.authentication.session.application.refresh.RefreshCredentialIssuanceUnavailableException;
import com.philia.flashsale.authentication.websupport.context.AuthenticationRequestContext;
import com.philia.flashsale.authentication.websupport.error.AuthenticationApiResponse;
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
/** HTTP adapter for cookie-based refresh rotation and replacement-cookie writing. */
public class RefreshController {
    private final RefreshSessionUseCase useCase;
    private final RefreshCookieWriter cookieWriter;

    public RefreshController(RefreshSessionUseCase useCase, RefreshCookieWriter cookieWriter) {
        this.useCase = useCase; this.cookieWriter = cookieWriter;
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthenticationApiResponse<TokenResponse>> refresh(HttpServletRequest request,
                                                                               HttpServletResponse response) {
        String raw = cookie(request, "refresh_token");
        RefreshSessionResult result;
        try {
            result = useCase.refresh(new RefreshSessionCommand(raw));
        } catch (RefreshCredentialIssuanceUnavailableException exception) {
            // Rotation already committed; remove the unusable browser credential before returning 503.
            cookieWriter.clear(response);
            throw exception;
        }
        cookieWriter.write(response, result.refreshCredential(), result.refreshMaxAgeSeconds());
        Object trace = request.getAttribute(AuthenticationRequestContext.TRACE_ATTRIBUTE);
        return ResponseEntity.ok(new AuthenticationApiResponse<>(
                new TokenResponse("Bearer", result.accessToken(), result.expiresIn()), String.valueOf(trace)));
    }

    private String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
}
