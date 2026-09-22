package com.philia.flashsale.authentication.account.application.profile;

/** Inbound application capability for updating the authenticated visible profile. */
public interface UpdateAccountDetailsUseCase {
    AccountProfile update(UpdateAccountDetailsCommand command);
}
