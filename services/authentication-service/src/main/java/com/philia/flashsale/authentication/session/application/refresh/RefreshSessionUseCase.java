package com.philia.flashsale.authentication.session.application.refresh;

/** Inbound application port for refresh-token rotation. */
public interface RefreshSessionUseCase {
    RefreshSessionResult refresh(RefreshSessionCommand command);
}
