package com.philia.flashsale.campaign.campaign.application.result;

import java.util.List;

/** Framework-neutral application page. */
public record PublicCampaignPage<T>(
        List<T> data,
        int number,
        int size,
        long totalElements) {

    public PublicCampaignPage {
        data = data == null ? List.of() : List.copyOf(data);
    }

    public <R> PublicCampaignPage<R> map(java.util.function.Function<T, R> mapper) {
        return new PublicCampaignPage<>(data.stream().map(mapper).toList(), number, size, totalElements);
    }
}
