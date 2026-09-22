package com.philia.flashsale.authentication.session.adapter.in.web;

import com.philia.flashsale.authentication.session.application.login.AuthenticateAccountUseCase;
import com.philia.flashsale.authentication.session.application.login.AuthenticationResult;
import com.philia.flashsale.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "flashsale.authentication.http", name = "enabled", havingValue = "true", matchIfMissing = true)
/** HTTP adapter for login; captures bounded metadata and writes the refresh cookie. */
@Tag(name = "Authentication")
public class LoginController {
    private final AuthenticateAccountUseCase useCase;
    private final RefreshCookieWriter cookieWriter;

    public LoginController(AuthenticateAccountUseCase useCase, RefreshCookieWriter cookieWriter) {
        this.useCase = useCase; this.cookieWriter = cookieWriter;
    }

    @PostMapping("/login")
    @Operation(summary = "Login and issue an access token", description = "Authenticates a shopper and sets the HttpOnly refresh cookie.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Access token issued"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Login rate limit exceeded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Authentication dependency unavailable")
    })
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request,
                                                                            HttpServletRequest httpRequest,
                                                                            HttpServletResponse httpResponse) {
        String userAgent = httpRequest.getHeader("User-Agent");
        String directIp = httpRequest.getRemoteAddr();
        AuthenticationResult result = useCase.authenticate(LoginWebMapper.toCommand(request, userAgent, directIp));
        cookieWriter.write(httpResponse, result.refreshCredential(), result.refreshMaxAgeSeconds());
        return ResponseEntity.ok(ApiResponse.success(LoginWebMapper.toResponse(result)));
    }
}
