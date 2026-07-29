package com.philia.flashsale.authentication.account.adapter.in.web;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
/** Validated registration DTO that also detects caller-supplied privilege fields. */
public class RegisterRequest {
    @NotBlank @Email @Size(max = 320)
    private String email;
    @Size(max = 100)
    private String username;
    @NotBlank
    private String password;
    private final Set<String> forbiddenFields = new HashSet<>();

    public RegisterRequest() { }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    @JsonAnySetter
    public void captureUnknown(String name, Object value) { forbiddenFields.add(name); }
    public boolean hasForbiddenFields() { return forbiddenFields.stream().anyMatch(name ->
            Set.of("role", "roles", "authority", "authorities").contains(name)); }
}
