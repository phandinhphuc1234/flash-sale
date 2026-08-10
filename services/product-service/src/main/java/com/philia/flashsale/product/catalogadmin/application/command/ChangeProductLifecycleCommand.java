package com.philia.flashsale.product.catalogadmin.application.command;

import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.philia.flashsale.product.catalogadmin.domain.AdminCommandName;
import com.philia.flashsale.product.catalogadmin.domain.CatalogAdminActor;
import com.philia.flashsale.product.catalogadmin.domain.TraceId;

public record ChangeProductLifecycleCommand(UUID productId, long expectedVersion,
        String idempotencyKey, CatalogAdminActor actor, TraceId traceId, AdminCommandName commandName) {
    public String requestHash() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((commandName + "\n" + productId + "\n" + expectedVersion)
                            .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }
}
