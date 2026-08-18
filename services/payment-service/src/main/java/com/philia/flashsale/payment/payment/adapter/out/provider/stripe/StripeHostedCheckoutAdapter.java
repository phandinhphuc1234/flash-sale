package com.philia.flashsale.payment.payment.adapter.out.provider.stripe;

import com.philia.flashsale.payment.configuration.StripeCheckoutProperties;
import com.philia.flashsale.payment.payment.application.exception.HostedCheckoutProviderException;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutCreateRequest;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutExpireRequest;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutResult;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutRetrieveRequest;
import com.philia.flashsale.payment.payment.application.model.provider.ProviderFailureCategory;
import com.philia.flashsale.payment.payment.application.port.out.HostedCheckoutProviderPort;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Stripe adapter for hosted card Checkout; the SDK is deliberately contained in this package. */
@Component
@ConditionalOnProperty(name = "payment.checkout.enabled", havingValue = "true")
@ConditionalOnProperty(name = "payment.stripe.enabled", havingValue = "true")
public final class StripeHostedCheckoutAdapter implements HostedCheckoutProviderPort {
    private final StripeClient client;
    private final StripeCheckoutProperties properties;
    private final StripeCheckoutMapper mapper;
    private final StripeFailureClassifier classifier;

    public StripeHostedCheckoutAdapter(StripeClient client, StripeCheckoutProperties properties,
            StripeCheckoutMapper mapper, StripeFailureClassifier classifier) {
        this.client = client;
        this.properties = properties;
        this.mapper = mapper;
        this.classifier = classifier;
    }

    @Override
    public HostedCheckoutResult create(HostedCheckoutCreateRequest request) {
        try {
            var session = client.checkout().sessions().create(mapper.toCreateParams(request),
                    mapper.toRequestOptions(request, properties));
            return mapper.toResult(session, Instant.now());
        } catch (StripeException exception) {
            return classify(exception);
        }
    }

    @Override
    public HostedCheckoutResult retrieve(HostedCheckoutRetrieveRequest request) {
        try {
            var session = client.checkout().sessions().retrieve(request.providerSessionId(),
                    mapper.toRequestOptions(properties));
            return mapper.toResult(session, Instant.now());
        } catch (StripeException exception) {
            return classify(exception);
        }
    }

    @Override
    public HostedCheckoutResult expire(HostedCheckoutExpireRequest request) {
        try {
            var session = client.checkout().sessions().expire(request.providerSessionId(),
                    mapper.toRequestOptions(properties));
            return mapper.toResult(session, Instant.now());
        } catch (StripeException exception) {
            return classify(exception);
        }
    }

    private HostedCheckoutResult classify(StripeException exception) {
        ProviderFailureCategory category = classifier.classify(exception);
        if (category == ProviderFailureCategory.INVALID_REQUEST
                || category == ProviderFailureCategory.AUTHENTICATION) {
            throw new HostedCheckoutProviderException(category);
        }
        return mapper.unknown(category, Instant.now());
    }
}
