package com.philia.flashsale.inventory.regularhold.adapter.in.web;

import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.inventory.regularhold.adapter.in.web.mapper.RegularStockHoldWebMapper;
import com.philia.flashsale.inventory.regularhold.adapter.in.web.request.CreateRegularStockHoldRequest;
import com.philia.flashsale.inventory.regularhold.adapter.in.web.response.RegularStockHoldResponse;
import com.philia.flashsale.inventory.regularhold.application.port.in.CreateRegularStockHoldUseCase;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Order-only controller; disabled until the reviewed regular-purchase intake release. */
@RestController
@RequestMapping("/internal/v1/regular-stock-holds")
@ConditionalOnProperty(name = "flashsale.inventory.regular-hold.api-enabled", havingValue = "true")
public class RegularStockHoldController implements RegularStockHoldApi {
    private final CreateRegularStockHoldUseCase createRegularStockHold;
    private final RegularStockHoldWebMapper mapper;

    public RegularStockHoldController(CreateRegularStockHoldUseCase createRegularStockHold,
            RegularStockHoldWebMapper mapper) {
        this.createRegularStockHold = createRegularStockHold;
        this.mapper = mapper;
    }

    @PostMapping
    @Override
    public ResponseEntity<ApiResponse<RegularStockHoldResponse>> create(
            @Valid @RequestBody CreateRegularStockHoldRequest request) {
        var result = createRegularStockHold.create(mapper.toCommand(request));
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .body(ApiResponse.success("Regular stock held", mapper.toResponse(result)));
    }
}
