package com.philia.flashsale.authentication.account.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/** Additive profile-write contract for visible contact details. */
@Schema(name = "UpdateAccountDetailsRequest",
        description = "Editable username and contact fields. Email, roles, status, identifiers, and credentials are excluded.")
@JsonIgnoreProperties(ignoreUnknown = false)
public class UpdateAccountDetailsRequest {
    @Schema(description = "Optional visible username. Must be unique when supplied.", example = "phuc.dev")
    @Size(max = 100)
    private String username;
    @Schema(description = "Optional full name. An empty value clears it.", example = "Phuc Nguyen")
    @Size(max = 150)
    private String fullName;
    @Schema(description = "Optional contact phone. An empty value clears it.", example = "+84901234567")
    @Size(max = 32)
    private String phone;
    @Schema(description = "Optional contact or delivery address. An empty value clears it.", example = "Thu Duc, Ho Chi Minh City")
    @Size(max = 500)
    private String address;
    private boolean usernameProvided;
    private boolean fullNameProvided;
    private boolean phoneProvided;
    private boolean addressProvided;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; this.usernameProvided = true; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; this.fullNameProvided = true; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; this.phoneProvided = true; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; this.addressProvided = true; }
    public boolean isUsernameProvided() { return usernameProvided; }
    public boolean isFullNameProvided() { return fullNameProvided; }
    public boolean isPhoneProvided() { return phoneProvided; }
    public boolean isAddressProvided() { return addressProvided; }

    @AssertTrue(message = "At least one editable profile field is required")
    @Schema(hidden = true)
    public boolean hasEditableField() {
        return usernameProvided || fullNameProvided || phoneProvided || addressProvided;
    }

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("Only username, fullName, phone, and address can be updated");
    }
}
