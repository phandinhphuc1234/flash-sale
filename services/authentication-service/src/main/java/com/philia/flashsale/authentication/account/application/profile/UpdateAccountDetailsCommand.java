package com.philia.flashsale.authentication.account.application.profile;

import java.util.UUID;

/** Transport-independent input for the authenticated editable profile fields. */
public record UpdateAccountDetailsCommand(
        UUID accountId,
        String username,
        boolean usernameProvided,
        String fullName,
        boolean fullNameProvided,
        String phone,
        boolean phoneProvided,
        String address,
        boolean addressProvided) {

    public boolean hasEditableField() {
        return usernameProvided || fullNameProvided || phoneProvided || addressProvided;
    }
}
