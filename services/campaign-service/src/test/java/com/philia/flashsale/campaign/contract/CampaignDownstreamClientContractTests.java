package com.philia.flashsale.campaign.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Test-first contract markers for Campaign's outbound Product/Inventory clients.
 *
 * <p>The client adapters are deliberately absent until T053-T055. These assertions keep the
 * required boundary visible before implementation and prevent the later client from relaying an
 * administrator token.</p>
 */
class CampaignDownstreamClientContractTests {

    @Test
    void serviceTokenManagerMustExistBeforeDownstreamClientsAreWired() {
        assertThat(typeExists(
                "com.philia.flashsale.campaign.security.serviceidentity.CampaignServiceTokenManager"))
                .as("Campaign must obtain a short-lived service token, not relay an admin token")
                .isTrue();
    }

    @Test
    void productAndInventoryClientAdaptersMustBeSeparateBoundaries() {
        assertThat(packageContainsType(
                "com.philia.flashsale.campaign.campaign.adapter.out.client.product"))
                .as("Product calls must use a dedicated outbound adapter")
                .isTrue();
        assertThat(packageContainsType(
                "com.philia.flashsale.campaign.campaign.adapter.out.client.inventory"))
                .as("Inventory calls must use a dedicated outbound adapter")
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

    private static boolean packageContainsType(String packageName) {
        // The concrete adapter names are intentionally not prescribed by the contract yet.
        return typeExists(packageName + ".CampaignProductValidationClient")
                || typeExists(packageName + ".CampaignInventoryAllocationClient");
    }
}
