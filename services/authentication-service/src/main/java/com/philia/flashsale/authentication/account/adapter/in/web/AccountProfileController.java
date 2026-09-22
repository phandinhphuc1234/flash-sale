package com.philia.flashsale.authentication.account.adapter.in.web;

import java.util.UUID;

import com.philia.flashsale.authentication.account.application.profile.LoadAccountProfileUseCase;
import com.philia.flashsale.authentication.account.domain.AccountFailure;
import com.philia.flashsale.common.web.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "flashsale.authentication.http", name = "enabled", havingValue = "true", matchIfMissing = true)
/** Authenticated HTTP adapter for the safe account summary query. */
@Tag(name = "Account profile", description = "Authenticated account identity and session-safe profile data.")
@SecurityRequirement(name = "bearerAuth")
public class AccountProfileController {
    private final LoadAccountProfileUseCase useCase;

    public AccountProfileController(LoadAccountProfileUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/me")
    @Operation(summary = "Read my account profile",
            description = "Returns username, email, status, and authorities without credentials or session details.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account profile returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing, invalid, or expired access token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Authenticated account does not exist")
    })
    public ResponseEntity<ApiResponse<AccountProfileResponse>> me(@AuthenticationPrincipal Jwt jwt) {
        UUID accountId;
        try {
            accountId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AccountFailure("AUTH_ACCOUNT_NOT_FOUND", "Account was not found");
        }
        AccountProfileResponse response = AccountProfileWebMapper.toResponse(useCase.load(accountId));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response));
    }
}
