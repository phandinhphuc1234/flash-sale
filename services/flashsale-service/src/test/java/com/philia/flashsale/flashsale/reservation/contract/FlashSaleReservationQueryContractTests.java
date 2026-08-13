package com.philia.flashsale.flashsale.reservation.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.philia.flashsale.flashsale.reservation.adapter.in.web.FlashSaleReservationController;
import com.philia.flashsale.flashsale.reservation.adapter.in.web.mapper.ReservationWebMapper;
import com.philia.flashsale.flashsale.reservation.application.port.in.GetOwnedReservationUseCase;
import com.philia.flashsale.flashsale.reservation.application.port.in.SubmitReservationUseCase;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationDetailsResult;
import com.philia.flashsale.flashsale.reservation.domain.model.ReservationStatus;
import com.philia.flashsale.flashsale.websupport.error.FlashSaleHttpExceptionHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mapstruct.factory.Mappers;
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

@WebMvcTest(controllers = FlashSaleReservationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({FlashSaleHttpExceptionHandler.class, FlashSaleReservationQueryContractTests.TestConfiguration.class})
class FlashSaleReservationQueryContractTests {
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID RESERVATION_ID = UUID.randomUUID();
    private static final UUID FOREIGN_RESERVATION_ID = UUID.randomUUID();
    private static final UUID UNKNOWN_RESERVATION_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubmitReservationUseCase submitReservation;

    @MockBean
    private GetOwnedReservationUseCase getOwnedReservation;

    @BeforeEach
    void authenticateControllerInvocation() {
        Jwt token = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(OWNER_ID.toString())
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
    void returnsTheOwnerReservationSnapshotWithNoStoreAndJwtSubjectIdentity() throws Exception {
        ReservationDetailsResult result = result();
        org.mockito.Mockito.when(getOwnedReservation.getOwnedReservation(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(result));

        mockMvc.perform(get("/api/v1/flash-sales/reservations/{reservationId}", RESERVATION_ID)
                        .with(jwt().jwt(token -> token.subject(OWNER_ID.toString())))
                        .header("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Reservation retrieved"))
                .andExpect(jsonPath("$.data.purchaseRequestId").value(result.purchaseRequestId().toString()))
                .andExpect(jsonPath("$.data.reservationId").value(RESERVATION_ID.toString()))
                .andExpect(jsonPath("$.data.campaignId").value(result.campaignId().toString()))
                .andExpect(jsonPath("$.data.variantId").value(result.variantId().toString()))
                .andExpect(jsonPath("$.data.sku").value("PHONE-BLACK-128"))
                .andExpect(content().string(containsString("\"unitPrice\":12990000.0000")))
                .andExpect(jsonPath("$.data.currency").value("VND"))
                .andExpect(jsonPath("$.data.quantity").value(1))
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andExpect(jsonPath("$.data.acceptedAt").value("2026-08-14T12:00:00Z"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-08-14T12:05:00Z"));

        org.mockito.Mockito.verify(getOwnedReservation).getOwnedReservation(
                org.mockito.ArgumentMatchers.argThat(query -> query.reservationId().equals(RESERVATION_ID)
                        && query.userId().equals(OWNER_ID)));
    }

    @Test
    void makesUnknownAndForeignReservationsIndistinguishableNotFoundResponses() throws Exception {
        org.mockito.Mockito.when(getOwnedReservation.getOwnedReservation(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());

        String foreign = mockMvc.perform(get("/api/v1/flash-sales/reservations/{reservationId}",
                        FOREIGN_RESERVATION_ID).with(jwt().jwt(token -> token.subject(OWNER_ID.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FLASH_SALE_RESERVATION_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();

        String unknown = mockMvc.perform(get("/api/v1/flash-sales/reservations/{reservationId}",
                        UNKNOWN_RESERVATION_ID).with(jwt().jwt(token -> token.subject(OWNER_ID.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FLASH_SALE_RESERVATION_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode foreignBody = (ObjectNode) mapper.readTree(foreign);
        ObjectNode unknownBody = (ObjectNode) mapper.readTree(unknown);
        foreignBody.remove("timestamp");
        unknownBody.remove("timestamp");
        assertThat(unknownBody).isEqualTo(foreignBody);
    }

    private static ReservationDetailsResult result() {
        Instant acceptedAt = Instant.parse("2026-08-14T12:00:00Z");
        return new ReservationDetailsResult(UUID.randomUUID(), RESERVATION_ID, UUID.randomUUID(), UUID.randomUUID(),
                "PHONE-BLACK-128", new BigDecimal("12990000.0000"), "VND", 1, ReservationStatus.RESERVED,
                acceptedAt, acceptedAt.plusSeconds(300));
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class TestConfiguration {
        @Bean
        ReservationWebMapper reservationWebMapper() {
            return Mappers.getMapper(ReservationWebMapper.class);
        }
    }
}
