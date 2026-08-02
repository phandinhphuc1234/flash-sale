package com.philia.flashsale.campaign.scheduleoperation.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Test-first markers for the schedule orchestrator and its transaction boundaries. */
class ScheduleCampaignUseCaseTests {

    @Test
    void scheduleOrchestratorMustBeIntroducedOutsideRemoteCallTransactions() {
        assertThat(typeExists(
                "com.philia.flashsale.campaign.campaign.application.usecase.ScheduleCampaignService"))
                .as("Schedule orchestration must be explicit before Product/Inventory calls are added")
                .isTrue();
    }

    @Test
    void operationPreparationMustProvideStableFingerprintAndInventoryRequestIdentity() {
        assertThat(typeExists(
                "com.philia.flashsale.campaign.scheduleoperation.application.usecase.PrepareScheduleOperationService"))
                .as("Schedule operation preparation must own idempotency and stable request identity")
                .isTrue();
    }

    private static boolean typeExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }
}
