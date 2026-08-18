package com.philia.flashsale.payment.payment.application.service;

import com.philia.flashsale.payment.payment.application.exception.HostedCheckoutProviderException;
import com.philia.flashsale.payment.payment.application.model.StartCheckoutCommand;
import com.philia.flashsale.payment.payment.application.model.StartCheckoutResult;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutResult;
import com.philia.flashsale.payment.payment.application.port.in.StartCheckoutUseCase;
import com.philia.flashsale.payment.payment.application.port.out.HostedCheckoutProviderPort;
import java.util.Objects;

/** Coordinates durable allocation, provider I/O, and convergence without holding a DB transaction over I/O. */
public final class StartCheckoutService implements StartCheckoutUseCase {
    private final CheckoutPersistenceService persistence;
    private final HostedCheckoutProviderPort provider;

    public StartCheckoutService(CheckoutPersistenceService persistence, HostedCheckoutProviderPort provider) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    @Override
    public StartCheckoutResult start(StartCheckoutCommand command) {
        Objects.requireNonNull(command, "command");
        CheckoutPersistenceService.Allocation allocation = persistence.allocate(command);
        HostedCheckoutResult providerResult;
        try {
            providerResult = allocation.retrieveRequest() != null
                    ? provider.retrieve(allocation.retrieveRequest())
                    : provider.create(allocation.createRequest());
        } catch (HostedCheckoutProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            providerResult = HostedCheckoutResult.unknown(
                    com.philia.flashsale.payment.payment.application.model.provider.ProviderFailureCategory.UNKNOWN,
                    java.time.Instant.now());
        }
        CheckoutPersistenceService.StartCheckoutSnapshot snapshot = persistence.converge(allocation, providerResult);
        StartCheckoutResult.Outcome outcome = switch (snapshot.kind()) {
            case CREATED -> StartCheckoutResult.Outcome.CREATED;
            case REPLAYED -> StartCheckoutResult.Outcome.REPLAYED;
            case RECOVERING -> StartCheckoutResult.Outcome.RECOVERING;
        };
        return new StartCheckoutResult(outcome, allocation.paymentId(), snapshot.paymentStatus(),
                snapshot.checkoutUrl(), snapshot.paymentDeadline());
    }
}
