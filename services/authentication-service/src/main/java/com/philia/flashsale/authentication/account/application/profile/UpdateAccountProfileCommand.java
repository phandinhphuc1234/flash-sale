package com.philia.flashsale.authentication.account.application.profile;

import java.util.UUID;

/** Transport-independent input for the authenticated username update. */
public record UpdateAccountProfileCommand(UUID accountId, String username) { }
