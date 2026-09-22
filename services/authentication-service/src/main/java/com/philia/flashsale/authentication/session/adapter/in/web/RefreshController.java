package com.philia.flashsale.authentication.session.adapter.in.web;

import com.philia.flashsale.authentication.session.application.refresh.RefreshSessionCommand;
import com.philia.flashsale.authentication.session.application.refresh.RefreshSessionResult;
import com.philia.flashsale.authentication.session.application.refresh.RefreshSessionUseCase;
import com.philia.flashsale.authentication.session.application.refresh.RefreshCredentialIssuanceUnavailableException;
import com.philia.flashsale.common.web.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "flashsale.authentication.http", name = "enabled", havingValue = "true", matchIfMissing = true)
/** HTTP adapter for cookie-based refresh rotation and replacement-cookie writing. */
@Tag(name = "Authentication")
public class RefreshController {
    private final RefreshSessionUseCase useCase;
    private final RefreshCookieWriter cookieWriter;

    public RefreshController(RefreshSessionUseCase useCase, RefreshCookieWriter cookieWriter) {
        this.useCase = useCase; this.cookieWriter = cookieWriter;
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh an access token", description = "Rotates the HttpOnly refresh cookie and returns a new access token.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Access token refreshed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Refresh credential is invalid or reused"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Authentication dependency unavailable")
    })
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(HttpServletRequest request,
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
        return ResponseEntity.ok(ApiResponse.success(
                new TokenResponse("Bearer", result.accessToken(), result.expiresIn())));
    }

    private String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
}
