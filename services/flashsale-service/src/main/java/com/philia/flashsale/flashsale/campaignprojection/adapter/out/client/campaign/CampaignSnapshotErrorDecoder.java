package com.philia.flashsale.flashsale.campaignprojection.adapter.out.client.campaign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.time.Duration;

/** Converts Campaign HTTP status/error codes into adapter-owned failures without leaking bodies. */
final class CampaignSnapshotErrorDecoder implements ErrorDecoder {
    private final ObjectMapper objectMapper;

    CampaignSnapshotErrorDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        CampaignSnapshotRemoteException.Failure failure = switch (status) {
            case 401, 403 -> CampaignSnapshotRemoteException.Failure.SECURITY;
            case 404 -> CampaignSnapshotRemoteException.Failure.NOT_FOUND;
            case 409 -> CampaignSnapshotRemoteException.Failure.NOT_RECOVERABLE;
            case 429 -> CampaignSnapshotRemoteException.Failure.RATE_LIMITED;
            default -> CampaignSnapshotRemoteException.Failure.UNAVAILABLE;
        };
        Duration retryAfter = failure == CampaignSnapshotRemoteException.Failure.RATE_LIMITED
                ? retryAfter(response) : Duration.ofSeconds(5);
        return new CampaignSnapshotRemoteException(failure, status, retryAfter);
    }

    private Duration retryAfter(Response response) {
        String header = response.headers().getOrDefault("Retry-After", java.util.Set.of())
                .stream().findFirst().orElse(null);
        if (header != null) {
            try {
                long seconds = Long.parseLong(header);
                if (seconds > 0) {
                    return Duration.ofSeconds(Math.min(seconds, 300));
                }
            } catch (NumberFormatException ignored) {
                // Fall through to the bounded recovery interval.
            }
        }
        return Duration.ofSeconds(5);
    }

    @SuppressWarnings("unused")
    private String readErrorCode(Response response) {
        if (response.body() == null) {
            return null;
        }
        try (var input = response.body().asInputStream()) {
            JsonNode body = objectMapper.readTree(input);
            return body.path("errorCode").isTextual() ? body.path("errorCode").textValue() : null;
        } catch (IOException ignored) {
            return null;
        }
    }
}
