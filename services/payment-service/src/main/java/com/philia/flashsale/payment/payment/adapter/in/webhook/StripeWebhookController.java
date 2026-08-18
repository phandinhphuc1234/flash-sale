package com.philia.flashsale.payment.payment.adapter.in.webhook;

import com.philia.flashsale.payment.payment.application.exception.ProviderReceiptUnavailableException;
import com.philia.flashsale.payment.payment.application.exception.WebhookVerificationException;
import com.philia.flashsale.payment.payment.application.port.in.AcceptProviderEventUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Raw-byte Stripe protocol endpoint; acknowledgement is emitted only after receipt commit. */
@RestController
@RequestMapping("${payment.webhook-path:/webhooks/v1/payments/stripe}")
@ConditionalOnProperty(name = {"payment.acceptance.enabled", "payment.checkout.enabled",
        "payment.stripe.enabled"}, havingValue = "true")
public final class StripeWebhookController {
    private final StripeWebhookVerifier verifier;
    private final AcceptProviderEventUseCase acceptProviderEvent;

    public StripeWebhookController(StripeWebhookVerifier verifier,
            AcceptProviderEventUseCase acceptProviderEvent) {
        this.verifier = verifier;
        this.acceptProviderEvent = acceptProviderEvent;
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody byte[] rawBody,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        // Do not access request.getReader(): the byte[] body is the exact signed representation.
        if (rawBody == null || rawBody.length == 0) {
            return ResponseEntity.badRequest().build();
        }
        try {
            var verified = verifier.verify(rawBody, signature);
            acceptProviderEvent.accept(verified);
            return ResponseEntity.noContent().build();
        } catch (WebhookVerificationException exception) {
            return ResponseEntity.badRequest().build();
        } catch (ProviderReceiptUnavailableException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
