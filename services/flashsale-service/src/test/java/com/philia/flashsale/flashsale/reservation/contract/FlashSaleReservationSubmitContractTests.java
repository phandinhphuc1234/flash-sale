package com.philia.flashsale.flashsale.reservation.contract;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.philia.flashsale.flashsale.reservation.adapter.in.web.FlashSaleReservationController;
import com.philia.flashsale.flashsale.reservation.adapter.in.web.mapper.ReservationWebMapper;
import com.philia.flashsale.flashsale.reservation.application.port.in.SubmitReservationUseCase;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationSubmissionResult;
import com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot;
import com.philia.flashsale.flashsale.websupport.error.FlashSaleHttpExceptionHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.mapstruct.factory.Mappers;

@WebMvcTest(controllers = FlashSaleReservationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({FlashSaleHttpExceptionHandler.class, FlashSaleReservationSubmitContractTests.TestConfiguration.class})
class FlashSaleReservationSubmitContractTests {
    private static final UUID CAMPAIGN_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubmitReservationUseCase submitReservation;

    @BeforeEach
    void authenticateControllerInvocation() {
        Jwt token = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(USER_ID.toString())
                .issuedAt(Instant.now().minusSeconds(30))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returns202LocationAndNoStoreOnlyAfterDurableAcceptance() throws Exception {
        AcceptedReservationSnapshot snapshot = snapshot();
        org.mockito.Mockito.when(submitReservation.submit(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ReservationSubmissionResult(
                        ReservationSubmissionResult.Outcome.ACCEPTED_NEW, snapshot));

        mockMvc.perform(post("/api/v1/flash-sales/{campaignId}/reservations", CAMPAIGN_ID)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString())))
                        .header("Idempotency-Key", "key-001")
                        .header("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01")
                        .contentType("application/json")
                        .content("{\"variantId\":\"" + snapshot.variantId() + "\",\"quantity\":1}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/flash-sales/reservations/" + snapshot.reservationId()))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Reservation accepted"))
                .andExpect(jsonPath("$.data.purchaseRequestId").value(snapshot.purchaseRequestId().toString()))
                .andExpect(jsonPath("$.data.status").value("RESERVED"));
    }

    @Test
    void mapsAcceptancePendingToRetryable503() throws Exception {
        org.mockito.Mockito.when(submitReservation.submit(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ReservationSubmissionResult(
                        ReservationSubmissionResult.Outcome.ACCEPTANCE_PENDING, null));

        mockMvc.perform(post("/api/v1/flash-sales/{campaignId}/reservations", CAMPAIGN_ID)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString())))
                        .header("Idempotency-Key", "key-pending")
                        .contentType("application/json")
                        .content("{\"variantId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.errorCode").value("FLASH_SALE_ACCEPTANCE_PENDING"));
    }

    @Test
    void mapsChangedIdempotencyRequestToConflict() throws Exception {
        org.mockito.Mockito.when(submitReservation.submit(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ReservationSubmissionResult(
                        ReservationSubmissionResult.Outcome.IDEMPOTENCY_CONFLICT, null));

        mockMvc.perform(post("/api/v1/flash-sales/{campaignId}/reservations", CAMPAIGN_ID)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString())))
                        .header("Idempotency-Key", "key-conflict")
                        .contentType("application/json")
                        .content("{\"variantId\":\"" + UUID.randomUUID() + "\",\"quantity\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("FLASH_SALE_IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void replayReturnsTheSameAcceptedIdentityAndLocation() throws Exception {
        AcceptedReservationSnapshot snapshot = snapshot();
        org.mockito.Mockito.when(submitReservation.submit(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ReservationSubmissionResult(
                        ReservationSubmissionResult.Outcome.ACCEPTED_REPLAY, snapshot));

        mockMvc.perform(post("/api/v1/flash-sales/{campaignId}/reservations", CAMPAIGN_ID)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString())))
                        .header("Idempotency-Key", "key-replay")
                        .contentType("application/json")
                        .content("{\"variantId\":\"" + snapshot.variantId() + "\",\"quantity\":1}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/flash-sales/reservations/" + snapshot.reservationId()))
                .andExpect(jsonPath("$.data.purchaseRequestId").value(snapshot.purchaseRequestId().toString()))
                .andExpect(jsonPath("$.data.reservationId").value(snapshot.reservationId().toString()));
    }

    @Test
    void mapsExpiredWinnerToConflict() throws Exception {
        org.mockito.Mockito.when(submitReservation.submit(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ReservationSubmissionResult(
                        ReservationSubmissionResult.Outcome.RESERVATION_EXPIRED, null));

        mockMvc.perform(post("/api/v1/flash-sales/{campaignId}/reservations", CAMPAIGN_ID)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString())))
                        .header("Idempotency-Key", "key-expired")
                        .contentType("application/json")
                        .content("{\"variantId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("FLASH_SALE_RESERVATION_EXPIRED"));
    }

    @Test
    void rejectsMissingIdempotencyKeyAndInvalidQuantity() throws Exception {
        mockMvc.perform(post("/api/v1/flash-sales/{campaignId}/reservations", CAMPAIGN_ID)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString())))
                        .contentType("application/json")
                        .content("{\"variantId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REQUIRED"));

        mockMvc.perform(post("/api/v1/flash-sales/{campaignId}/reservations", CAMPAIGN_ID)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString())))
                        .header("Idempotency-Key", "key-invalid")
                        .contentType("application/json")
                        .content("{\"variantId\":\"" + UUID.randomUUID() + "\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("quantity"));

        mockMvc.perform(post("/api/v1/flash-sales/{campaignId}/reservations", CAMPAIGN_ID)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString())))
                        .header("Idempotency-Key", " ".repeat(3))
                        .contentType("application/json")
                        .content("{\"variantId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REQUIRED"));

        mockMvc.perform(post("/api/v1/flash-sales/{campaignId}/reservations", CAMPAIGN_ID)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString())))
                        .header("Idempotency-Key", "x".repeat(129))
                        .contentType("application/json")
                        .content("{\"variantId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    private AcceptedReservationSnapshot snapshot() {
        Instant accepted = Instant.parse("2026-08-13T12:00:00Z");
        return new AcceptedReservationSnapshot(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), CAMPAIGN_ID, UUID.randomUUID(), USER_ID,
                UUID.randomUUID(), "SKU-001", BigDecimal.ONE.setScale(4), "VND", 1,
                "a".repeat(64), "b".repeat(64), accepted, accepted.plusSeconds(300), accepted.plusSeconds(90000),
                null, null);
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class TestConfiguration {
        @Bean
        ReservationWebMapper reservationWebMapper() {
            return Mappers.getMapper(ReservationWebMapper.class);
        }
    }
}
