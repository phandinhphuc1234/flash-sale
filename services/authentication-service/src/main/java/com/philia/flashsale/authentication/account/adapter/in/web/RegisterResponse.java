package com.philia.flashsale.authentication.account.adapter.in.web;

import java.util.UUID;

/** Safe registration HTTP response DTO. */
public record RegisterResponse(UUID userId, String email, String username, String role, String status) { }
