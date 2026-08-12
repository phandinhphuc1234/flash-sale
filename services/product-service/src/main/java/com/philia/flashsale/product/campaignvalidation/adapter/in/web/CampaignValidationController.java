package com.philia.flashsale.product.campaignvalidation.adapter.in.web;

import com.philia.flashsale.product.campaignvalidation.application.port.in.ValidateCampaignVariantUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Class use for validating campaign variant IDs in the context of product service, ensuring that the provided variant ID is associated with an active campaign. 
// This controller serves as an entry point for external requests to validate campaign variants, delegating the actual validation logic to the application 
// layer through the ValidateCampaignVariantUseCase interface.
@RestController
@RequestMapping(path = "/internal/v1/catalog/variants", produces = MediaType.APPLICATION_JSON_VALUE)
public class CampaignValidationController {
    private final ValidateCampaignVariantUseCase validation;

    public CampaignValidationController(ValidateCampaignVariantUseCase validation) {
        this.validation = validation;
    }

    @PostMapping(path = "/campaign-validation", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CampaignValidationResponse validate(@Valid @RequestBody CampaignValidationRequest request) {
        return CampaignValidationResponse.from(validation.validate(request.variantId()));
    }

    public record CampaignValidationRequest(@NotNull UUID variantId) {
    }
}
