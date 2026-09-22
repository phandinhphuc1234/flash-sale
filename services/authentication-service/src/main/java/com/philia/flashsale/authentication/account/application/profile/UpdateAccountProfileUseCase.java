package com.philia.flashsale.authentication.account.application.profile;

/** Inbound application capability for updating the authenticated account's username. */
public interface UpdateAccountProfileUseCase {
    AccountProfile update(UpdateAccountProfileCommand command);
}
