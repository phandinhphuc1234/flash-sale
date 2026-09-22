package com.philia.flashsale.authentication.account.adapter.in.web;

import java.util.UUID;

import com.philia.flashsale.authentication.account.application.profile.AccountProfile;
import com.philia.flashsale.authentication.account.application.profile.UpdateAccountDetailsCommand;
import com.philia.flashsale.authentication.account.application.profile.UpdateAccountDetailsUseCase;
import com.philia.flashsale.authentication.account.domain.AccountFailure;
import com.philia.flashsale.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated HTTP adapter for updating the visible account profile. */
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "flashsale.authentication.http", name = "enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "Account profile", description = "Authenticated account identity and session-safe profile data.")
@SecurityRequirement(name = "bearerAuth")
public class AccountProfileDetailsUpdateController {
    private final UpdateAccountDetailsUseCase useCase;

    public AccountProfileDetailsUpdateController(UpdateAccountDetailsUseCase useCase) {
        this.useCase = useCase;
    }

    @PatchMapping("/me/profile")
    @Operation(summary = "Update my profile details",
            description = "Updates username, full name, phone, and address. Email and security fields remain read-only.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Profile updated and safe profile returned",
                    headers = {
                            @Header(name = "Cache-Control", description = "Profile responses are never cached",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "no-store")),
                            @Header(name = "X-Trace-Id", description = "Request correlation identifier",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "trace-123"))
                    }),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or unknown profile field"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing, invalid, or expired access token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Authenticated account does not exist"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Username is already in use")
    })
    public ResponseEntity<ApiResponse<AccountProfileResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateAccountDetailsRequest request) {
        UUID accountId;
        try {
            accountId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AccountFailure("AUTH_ACCOUNT_NOT_FOUND", "Account was not found");
        }
        AccountProfile profile = useCase.update(new UpdateAccountDetailsCommand(accountId,
                request.getUsername(), request.isUsernameProvided(), request.getFullName(), request.isFullNameProvided(),
                request.getPhone(), request.isPhoneProvided(), request.getAddress(), request.isAddressProvided()));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(AccountProfileWebMapper.toResponse(profile)));
    }
}
