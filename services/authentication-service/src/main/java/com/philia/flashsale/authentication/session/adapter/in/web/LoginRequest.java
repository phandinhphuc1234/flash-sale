package com.philia.flashsale.authentication.session.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Validated login request DTO; it is not an application command or domain object. */
public record LoginRequest(@NotBlank @Size(max = 320) String login,
                           @NotBlank String password,
                           @Size(max = 150) String deviceName) { }
