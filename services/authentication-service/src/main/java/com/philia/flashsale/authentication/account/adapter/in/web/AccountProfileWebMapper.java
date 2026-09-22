package com.philia.flashsale.authentication.account.adapter.in.web;

import com.philia.flashsale.authentication.account.application.profile.AccountProfile;

public final class AccountProfileWebMapper {
    private AccountProfileWebMapper() { }

    public static AccountProfileResponse toResponse(AccountProfile profile) {
        return new AccountProfileResponse(profile.id(), profile.login(), profile.username(), profile.email(),
                profile.displayName(), profile.fullName(), profile.phone(), profile.address(), profile.status(), profile.authorities());
    }
}
