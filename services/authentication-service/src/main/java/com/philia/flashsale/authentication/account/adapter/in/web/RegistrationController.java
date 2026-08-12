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

import com.philia.flashsale.authentication.account.application.registration.RegisterAccountUseCase;
import com.philia.flashsale.authentication.account.domain.AccountFailure;
import com.philia.flashsale.common.web.ApiResponse;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "flashsale.authentication.http", name = "enabled", havingValue = "true", matchIfMissing = true)
/** HTTP adapter for public shopper registration. */
public class RegistrationController {
    private final RegisterAccountUseCase registerAccountUseCase;

    public RegistrationController(RegisterAccountUseCase registerAccountUseCase) {
        this.registerAccountUseCase = registerAccountUseCase;
    }

    @PostMapping("/register")
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
