package com.philia.flashsale.campaign.campaign.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.campaign.campaign.application.command.CreateCampaignCommand;
import com.philia.flashsale.campaign.campaign.application.command.ReplaceCampaignItemCommand;
import com.philia.flashsale.campaign.campaign.application.command.ReplaceCampaignMetadataCommand;
import com.philia.flashsale.campaign.campaign.application.exception.CampaignCodeAlreadyExistsException;
import com.philia.flashsale.campaign.campaign.application.exception.CampaignOperationInProgressException;
import com.philia.flashsale.campaign.campaign.application.port.out.CampaignActorPort;
import com.philia.flashsale.campaign.campaign.application.port.out.CampaignClockPort;
import com.philia.flashsale.campaign.campaign.application.port.out.CheckActiveCampaignOperationPort;
import com.philia.flashsale.campaign.campaign.application.port.out.CheckCampaignCodeUniquenessPort;
import com.philia.flashsale.campaign.campaign.application.port.out.LoadCampaignPort;
import com.philia.flashsale.campaign.campaign.application.port.out.SaveCampaignPort;
import com.philia.flashsale.campaign.campaign.application.query.GetCampaignDetailQuery;
import com.philia.flashsale.campaign.campaign.application.result.CampaignDetailResult;
import com.philia.flashsale.campaign.campaign.application.usecase.CampaignDraftApplicationService;
import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignMoney;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CampaignDraftUseCaseTests {

    private static final Instant START = Instant.parse("2026-08-01T10:00:00Z");
    private static final Instant END = Instant.parse("2026-08-01T11:00:00Z");
    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");

    @Mock
    private LoadCampaignPort loadCampaignPort;
    @Mock
    private SaveCampaignPort saveCampaignPort;
    @Mock
    private CheckCampaignCodeUniquenessPort codeUniquenessPort;
    @Mock
    private CheckActiveCampaignOperationPort activeOperationPort;
    @Mock
    private CampaignClockPort clockPort;
    @Mock
    private CampaignActorPort actorPort;

    private CampaignDraftApplicationService service;

    @BeforeEach
    void setUp() {
        service = new CampaignDraftApplicationService(
                loadCampaignPort,
                saveCampaignPort,
                codeUniquenessPort,
                activeOperationPort,
                clockPort,
                actorPort);
    }

    @Test
    void createsDraftWithoutDownstreamCalls() {
        when(actorPort.currentActor()).thenReturn("campaign-admin");
        when(clockPort.now()).thenReturn(NOW);
        when(codeUniquenessPort.existsByCode("SUMMER-01")).thenReturn(false);
        when(saveCampaignPort.save(any(Campaign.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CampaignDetailResult result = service.create(new CreateCampaignCommand(
                " summer-01 ", "Summer promotion", START, END));

        assertEquals("SUMMER-01", result.code());
        assertEquals(0, result.version());
        assertEquals("DRAFT", result.status().name());
        verify(saveCampaignPort).save(any(Campaign.class));
        verify(loadCampaignPort, never()).findById(any());
        verify(activeOperationPort, never()).hasActiveOperation(any());
    }

    @Test
    void replacesMetadataAndAdvancesVersion() {
        Campaign campaign = draft();
        when(loadCampaignPort.findById(campaign.id())).thenReturn(Optional.of(campaign));
        when(activeOperationPort.hasActiveOperation(campaign.id())).thenReturn(false);
        when(actorPort.currentActor()).thenReturn("campaign-admin");
        when(clockPort.now()).thenReturn(NOW);
        when(saveCampaignPort.save(any(Campaign.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CampaignDetailResult result = service.replaceMetadata(new ReplaceCampaignMetadataCommand(
                campaign.id(), 0, "Updated promotion", START.plusSeconds(300), END.plusSeconds(300)));

        assertEquals("Updated promotion", result.name());
        assertEquals(1, result.version());
        verify(saveCampaignPort).save(campaign);
    }

    @Test
    void replacesItemAndKeepsOnlyTheLatestDraftItem() {
        Campaign campaign = draft();
        when(loadCampaignPort.findById(campaign.id())).thenReturn(Optional.of(campaign));
        when(activeOperationPort.hasActiveOperation(campaign.id())).thenReturn(false);
        when(actorPort.currentActor()).thenReturn("campaign-admin");
        when(clockPort.now()).thenReturn(NOW);
        when(saveCampaignPort.save(any(Campaign.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CampaignDetailResult result = service.replaceItem(new ReplaceCampaignItemCommand(
                campaign.id(),
                0,
                UUID.randomUUID(),
                CampaignMoney.vnd(new BigDecimal("100")),
                10,
                2));

        assertEquals(1, result.version());
        assertEquals(10, result.item().requestedQuantity());
        assertEquals(2, result.item().purchaseLimitPerUser());
        assertEquals(0, result.item().allocatedQuantity());
        verify(saveCampaignPort).save(campaign);
    }

    @Test
    void returnsCurrentDetailWithoutMutationPorts() {
        Campaign campaign = draft();
        when(loadCampaignPort.findById(campaign.id())).thenReturn(Optional.of(campaign));

        CampaignDetailResult result = service.getDetail(new GetCampaignDetailQuery(campaign.id()));

        assertEquals(campaign.id(), result.id());
        assertEquals(campaign.code(), result.code());
        assertEquals(campaign.version(), result.version());
        verify(saveCampaignPort, never()).save(any());
        verify(codeUniquenessPort, never()).existsByCode(any());
        verify(activeOperationPort, never()).hasActiveOperation(any());
    }

    @Test
    void rejectsDuplicateCodeBeforeSaving() {
        when(actorPort.currentActor()).thenReturn("campaign-admin");
        when(clockPort.now()).thenReturn(NOW);
        when(codeUniquenessPort.existsByCode("SUMMER-01")).thenReturn(true);

        assertThrows(CampaignCodeAlreadyExistsException.class, () -> service.create(new CreateCampaignCommand(
                "summer-01", "Summer promotion", START, END)));

        verify(saveCampaignPort, never()).save(any());
    }

    @Test
    void blocksMetadataMutationWhenScheduleOperationIsActive() {
        Campaign campaign = draft();
        when(loadCampaignPort.findById(campaign.id())).thenReturn(Optional.of(campaign));
        when(activeOperationPort.hasActiveOperation(campaign.id())).thenReturn(true);

        assertThrows(CampaignOperationInProgressException.class,
                () -> service.replaceMetadata(new ReplaceCampaignMetadataCommand(
                        campaign.id(), 0, "Blocked", START, END)));

        assertEquals(0, campaign.version());
        verify(saveCampaignPort, never()).save(any());
    }

    private static Campaign draft() {
        return Campaign.createDraft(
                UUID.randomUUID(), "SUMMER-01", "Summer promotion", START, END, "seed", NOW);
    }
}
