package com.philia.flashsale.campaign.campaign.adapter.in.web.publicapi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.philia.flashsale.campaign.campaign.application.port.in.BrowsePublicCampaignsUseCase;
import com.philia.flashsale.campaign.campaign.application.port.in.GetPublicCampaignUseCase;
import com.philia.flashsale.campaign.campaign.application.port.out.CampaignClockPort;
import com.philia.flashsale.campaign.campaign.application.query.BrowsePublicCampaignsQuery;
import com.philia.flashsale.campaign.campaign.application.result.PublicCampaignPage;
import com.philia.flashsale.campaign.campaign.application.result.PublicCampaignResult;
import com.philia.flashsale.campaign.campaign.application.result.PublicCampaignState;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublicCampaignControllerTests {

    private MockMvc mvc;
    private BrowsePublicCampaignsUseCase browse;

    @BeforeEach
    void setUp() {
        browse = mock(BrowsePublicCampaignsUseCase.class);
        GetPublicCampaignUseCase detail = mock(GetPublicCampaignUseCase.class);
        CampaignClockPort clock = () -> Instant.parse("2026-10-09T10:30:00Z");
        mvc = MockMvcBuilders.standaloneSetup(new PublicCampaignController(browse, detail, clock)).build();
    }

    @Test
    void returnsSharedPaginatedEnvelopeWithoutExactStock() throws Exception {
        when(browse.browse(any(BrowsePublicCampaignsQuery.class)))
                .thenReturn(new PublicCampaignPage<>(List.of(result()), 0, 12, 1));

        mvc.perform(get("/api/v1/campaigns").param("phase", "LIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.data[0].phase").value("LIVE"))
                .andExpect(jsonPath("$.data.data[0].campaignPrice").value("179000"))
                .andExpect(jsonPath("$.data.data[0].remainingQuantity").doesNotExist())
                .andExpect(jsonPath("$.data.page.totalElements").value(1));
    }

    private PublicCampaignResult result() {
        return new PublicCampaignResult(
                UUID.randomUUID(), "Phone weekend", PublicCampaignState.LIVE,
                Instant.parse("2026-10-09T10:00:00Z"), Instant.parse("2026-10-09T11:00:00Z"),
                true, UUID.randomUUID(), UUID.randomUUID(), "PHONE-BLACK",
                new BigDecimal("229000"), new BigDecimal("179000"), "VND", 1, true);
    }
}
