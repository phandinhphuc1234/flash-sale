package com.philia.flashsale.authentication.account.application.profile;

import java.util.UUID;

public interface LoadAccountProfileUseCase {
    AccountProfile load(UUID accountId);
}
