package com.philia.flashsale.campaign.campaign.adapter.out.client.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.campaign.campaign.application.exception.CampaignDownstreamException;
import com.philia.flashsale.campaign.campaign.application.result.ValidatedCampaignVariant;
import com.philia.flashsale.campaign.security.serviceidentity.CampaignServiceTokenManager;
import com.philia.flashsale.campaign.security.serviceidentity.CampaignServiceTokenManager.Capability;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.Response;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CampaignProductValidationClientTests {

    @Mock
    private ProductFeignClient feignClient;
    @Mock
    private CampaignServiceTokenManager tokenManager;

    private CampaignProductValidationClient adapter;

    @BeforeEach
    void setUp() {
        adapter = new CampaignProductValidationClient(
                feignClient,
                Mappers.getMapper(ProductValidationClientMapper.class),
                tokenManager);
    }

    @Test
    void sendsNarrowServiceTokenAndTraceAndMapsProductSnapshot() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        ProductValidationResponse remote = new ProductValidationResponse(
                productId, variantId, "SKU-1", "ACTIVE", "ACTIVE", true,
                new BigDecimal("120000.0000"), "VND");
        when(tokenManager.authorizationHeader(Capability.PRODUCT_VALIDATION))
                .thenReturn("Bearer product-token");
        when(feignClient.validate(
                "Bearer product-token", "trace-product", new ProductValidationRequest(variantId)))
                .thenReturn(remote);

        ValidatedCampaignVariant result = adapter.validate(variantId, "trace-product");

        assertThat(result.productId()).isEqualTo(productId);
        assertThat(result.variantId()).isEqualTo(variantId);
        assertThat(result.sku()).isEqualTo("SKU-1");
        verify(feignClient).validate(
                "Bearer product-token", "trace-product", new ProductValidationRequest(variantId));
    }

    @Test
    void rejectsAResponseForAnotherVariant() {
        UUID requestedVariant = UUID.randomUUID();
        when(tokenManager.authorizationHeader(Capability.PRODUCT_VALIDATION))
                .thenReturn("Bearer product-token");
        when(feignClient.validate(
                "Bearer product-token", "trace-product", new ProductValidationRequest(requestedVariant)))
                .thenReturn(new ProductValidationResponse(
                        UUID.randomUUID(), UUID.randomUUID(), "SKU-2", "ACTIVE", "ACTIVE", true,
                        BigDecimal.TEN, "VND"));

        assertThatThrownBy(() -> adapter.validate(requestedVariant, "trace-product"))
                .isInstanceOf(CampaignDownstreamException.class)
                .extracting(exception -> ((CampaignDownstreamException) exception).failure())
                .isEqualTo(CampaignDownstreamException.Failure.PRODUCT_SERVICE_UNAVAILABLE);
    }

    @Test
    void mapsStableProductNotFoundErrorWithoutUsingItsMessage() {
        UUID variantId = UUID.randomUUID();
        when(tokenManager.authorizationHeader(Capability.PRODUCT_VALIDATION))
                .thenReturn("Bearer product-token");
        ProductRemoteException remoteFailure = (ProductRemoteException)
                new ProductFeignErrorDecoder(new ObjectMapper()).decode(
                        "ProductFeignClient#validate",
                        response(404, "{\"errorCode\":\"PRODUCT_VARIANT_NOT_FOUND\",\"message\":\"ignored\"}"));
        when(feignClient.validate(
                "Bearer product-token", "trace-product", new ProductValidationRequest(variantId)))
                .thenThrow(remoteFailure);

        assertThatThrownBy(() -> adapter.validate(variantId, "trace-product"))
                .isInstanceOf(CampaignDownstreamException.class)
                .extracting(exception -> ((CampaignDownstreamException) exception).failure())
                .isEqualTo(CampaignDownstreamException.Failure.PRODUCT_VARIANT_NOT_FOUND);
    }

    private Response response(int status, String body) {
        Request request = Request.create(
                Request.HttpMethod.POST,
                "http://product-service/internal/v1/catalog/variants/campaign-validation",
                Map.of(),
                (byte[]) null,
                StandardCharsets.UTF_8,
                null);
        return Response.builder()
                .request(request)
                .status(status)
                .reason("downstream failure")
                .body(body, StandardCharsets.UTF_8)
                .build();
    }
}
