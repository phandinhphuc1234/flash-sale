package com.philia.flashsale.authentication.account.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public profile-write contract; email and security fields are intentionally not editable here. */
@Schema(name = "UpdateAccountProfileRequest", description = "Editable account profile fields. Email, roles, status, identifiers, and credentials are intentionally excluded.")
@JsonIgnoreProperties(ignoreUnknown = false)
public class UpdateAccountProfileRequest {
    @Schema(description = "New visible username. Must be non-blank, unique, and at most 100 characters.",
            example = "phuc.dev", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Size(max = 100)
    private String username;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("Only the username field can be updated");
    }
}
