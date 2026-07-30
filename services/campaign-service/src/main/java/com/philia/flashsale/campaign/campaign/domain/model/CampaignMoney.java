package com.philia.flashsale.campaign.campaign.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Value object for positive Campaign prices in the approved VND market. */
public record CampaignMoney(BigDecimal amount, String currency) {

    public static final String VND = "VND";
    private static final int SCALE = 4;

    public CampaignMoney {
        if (amount == null) {
            throw new IllegalArgumentException("Campaign money amount is required");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Campaign money amount must be positive");
        }
        if (amount.scale() > SCALE) {
            throw new IllegalArgumentException("Campaign money supports at most 4 decimal places");
        }

        String normalizedCurrency = normalizeCurrency(currency);
        if (!VND.equals(normalizedCurrency)) {
            throw new IllegalArgumentException("Only VND Campaign money is supported");
        }

        amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        currency = normalizedCurrency;
    }

    public static CampaignMoney vnd(BigDecimal amount) {
        return new CampaignMoney(amount, VND);
    }

    public boolean isLessThan(CampaignMoney other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) < 0;
    }

    public boolean isSameCurrency(CampaignMoney other) {
        return other != null && currency.equals(other.currency);
    }

    private void requireSameCurrency(CampaignMoney other) {
        if (!isSameCurrency(other)) {
            throw new IllegalArgumentException("Campaign money currencies must match");
        }
    }

    private static String normalizeCurrency(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Campaign money currency is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
