package com.philia.flashsale.authentication.account.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.philia.flashsale.authentication.account.application.registration.RegisterAccountUseCase;
import com.philia.flashsale.authentication.account.domain.AccountFailure;
import com.philia.flashsale.common.web.ApiResponse;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "flashsale.authentication.http", name = "enabled", havingValue = "true", matchIfMissing = true)
/** HTTP adapter for public shopper registration. */
@Tag(name = "Authentication")
public class RegistrationController {
    private final RegisterAccountUseCase registerAccountUseCase;

    public RegistrationController(RegisterAccountUseCase registerAccountUseCase) {
        this.registerAccountUseCase = registerAccountUseCase;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a shopper account", description = "Creates a ROLE_USER account; privilege fields are never accepted from public registration.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Account registered"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid registration payload"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email or username is already in use")
    })
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        if (request.hasForbiddenFields()) {
            throw new AccountFailure(
                    "AUTH_VALIDATION_FAILED", "Privilege fields are not accepted");
        }
        RegisterResponse response = RegistrationWebMapper.toResponse(registerAccountUseCase.register(
                RegistrationWebMapper.toCommand(request)));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account registered", response));
    }
}
