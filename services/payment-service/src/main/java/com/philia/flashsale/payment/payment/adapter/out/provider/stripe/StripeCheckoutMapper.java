package com.philia.flashsale.payment.payment.adapter.out.provider.stripe;

import com.philia.flashsale.payment.configuration.StripeCheckoutProperties;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutCreateRequest;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutResult;
import com.philia.flashsale.payment.payment.application.model.provider.ProviderCheckoutState;
import com.philia.flashsale.payment.payment.application.model.provider.ProviderFailureCategory;
import com.philia.flashsale.payment.payment.domain.model.Money;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import java.time.Duration;
import java.time.Instant;

/** Maps trusted application values to Stripe and reduces responses to safe provider facts. */
public final class StripeCheckoutMapper {
    private static final Duration STRIPE_MINIMUM_EXPIRY = Duration.ofMinutes(30);

    public SessionCreateParams toCreateParams(HostedCheckoutCreateRequest request) {
        long amount = Money.of(request.amount(), request.currency()).toProviderMinorUnits();
        var price = new SessionCreateParams.LineItem.PriceData.Builder()
                .setCurrency(request.currency().toLowerCase(java.util.Locale.ROOT))
                .setUnitAmount(amount)
                .setProductData(new SessionCreateParams.LineItem.PriceData.ProductData.Builder()
                        .setName("Flash Sale Payment").build())
                .build();
        var line = new SessionCreateParams.LineItem.Builder().setPriceData(price).setQuantity(1L).build();
        long expiry = Math.max(request.paymentDeadline().getEpochSecond(),
                Instant.now().plus(STRIPE_MINIMUM_EXPIRY).getEpochSecond());
        var builder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setPaymentIntentData(new SessionCreateParams.PaymentIntentData.Builder()
                        .setCaptureMethod(SessionCreateParams.PaymentIntentData.CaptureMethod.AUTOMATIC)
                        .putAllMetadata(request.metadata()).build())
                .addLineItem(line)
                .setSuccessUrl(request.successUrl())
                .setCancelUrl(request.cancelUrl())
                .setExpiresAt(expiry)
                .putAllMetadata(request.metadata())
                .setClientReferenceId(request.paymentId());
        return builder.build();
    }

    public RequestOptions toRequestOptions(HostedCheckoutCreateRequest request,
            StripeCheckoutProperties properties) {
        var builder = RequestOptions.builder()
                .setIdempotencyKey(request.providerIdempotencyKey())
                .setConnectTimeout((int) properties.connectTimeout().toMillis())
                .setReadTimeout((int) properties.readTimeout().toMillis())
                .setMaxNetworkRetries(properties.maxNetworkRetries())
                .setBaseUrl(properties.apiBaseUrl().toString());
        RequestOptions.RequestOptionsBuilder.unsafeSetStripeVersionOverride(builder,
                properties.expectedApiVersion());
        return builder.build();
    }

    public RequestOptions toRequestOptions(StripeCheckoutProperties properties) {
        var builder = RequestOptions.builder()
                .setConnectTimeout((int) properties.connectTimeout().toMillis())
                .setReadTimeout((int) properties.readTimeout().toMillis())
                .setMaxNetworkRetries(properties.maxNetworkRetries())
                .setBaseUrl(properties.apiBaseUrl().toString());
        RequestOptions.RequestOptionsBuilder.unsafeSetStripeVersionOverride(builder,
                properties.expectedApiVersion());
        return builder.build();
    }

    public HostedCheckoutResult toResult(Session session, Instant observedAt) {
        ProviderCheckoutState state = switch (safe(session.getStatus())) {
            case "complete" -> ProviderCheckoutState.PAID;
            case "open" -> "paid".equalsIgnoreCase(safe(session.getPaymentStatus()))
                    ? ProviderCheckoutState.PAID : ProviderCheckoutState.OPEN;
            case "expired" -> ProviderCheckoutState.EXPIRED;
            default -> ProviderCheckoutState.UNKNOWN;
        };
        Instant expiresAt = session.getExpiresAt() == null ? null
                : Instant.ofEpochSecond(session.getExpiresAt());
        return new HostedCheckoutResult(state, session.getId(), session.getPaymentIntent(),
                session.getUrl(), expiresAt, observedAt, null);
    }

    public HostedCheckoutResult unknown(ProviderFailureCategory category, Instant observedAt) {
        return HostedCheckoutResult.unknown(category, observedAt);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
