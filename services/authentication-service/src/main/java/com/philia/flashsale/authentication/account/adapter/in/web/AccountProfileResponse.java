package com.philia.flashsale.authentication.account.adapter.in.web;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AccountProfileResponse", description = "Safe profile summary. Contains no password, refresh token, or access token.")
public record AccountProfileResponse(
        @Schema(description = "Internal account identifier; use username/email for display.") UUID id,
        @Schema(description = "Login identifier used to authenticate the account.") String login,
        @Schema(description = "User-selected display name.", example = "phuc.dev") String username,
        @Schema(description = "Account email address. Read-only in this API.", example = "phuc@example.com") String email,
        @Schema(description = "Resolved display name for clients that need a fallback.", example = "phuc.dev") String displayName,
        @Schema(description = "User's full name. Editable by the account owner.", example = "Phuc Nguyen") String fullName,
        @Schema(description = "User's contact phone. Editable by the account owner.", example = "+84901234567") String phone,
        @Schema(description = "User's delivery/contact address. Editable by the account owner.", example = "Thu Duc, Ho Chi Minh City") String address,
        @Schema(description = "Current account status.", example = "ACTIVE") String status,
        @Schema(description = "Granted application authorities.", example = "[ROLE_USER]") List<String> authorities) { }
