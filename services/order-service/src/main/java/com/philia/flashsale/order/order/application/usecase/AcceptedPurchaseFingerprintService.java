package com.philia.flashsale.order.order.application.usecase;

import com.philia.flashsale.order.order.application.command.CreateOrderFromAcceptedPurchaseCommand;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/** Canonical SHA-256 business fingerprint for semantic accepted-purchase equivalence. */
public final class AcceptedPurchaseFingerprintService {

    public String fingerprint(CreateOrderFromAcceptedPurchaseCommand command) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            append(output, command.eventType());
            append(output, Integer.toString(command.eventVersion()));
            append(output, command.producer());
            append(output, command.aggregateType());
            append(output, command.aggregateId().toString());
            append(output, Long.toString(command.aggregateVersion()));
            append(output, command.purchaseRequestId().toString());
            append(output, command.reservationId().toString());
            append(output, command.campaignId().toString());
            append(output, command.variantId().toString());
            append(output, command.userId().toString());
            append(output, Long.toString(command.quantity()));
            append(output, command.unitPrice().setScale(4, RoundingMode.UNNECESSARY).toPlainString());
            append(output, command.currency().toUpperCase(Locale.ROOT));
            append(output, canonicalInstant(command.acceptedAt()));
            append(output, canonicalInstant(command.expiresAt()));
            output.flush();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException exception) {
            throw new IllegalStateException("cannot encode canonical purchase fingerprint", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("unit price must be exactly representable at scale 4", exception);
        }
    }

    private void append(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private String canonicalInstant(Instant instant) {
        return Long.toString(instant.toEpochMilli());
    }
}
