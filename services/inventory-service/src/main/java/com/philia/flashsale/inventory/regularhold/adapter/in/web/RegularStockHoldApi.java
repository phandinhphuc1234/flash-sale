package com.philia.flashsale.inventory.regularhold.adapter.in.web;

import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.inventory.regularhold.adapter.in.web.request.CreateRegularStockHoldRequest;
import com.philia.flashsale.inventory.regularhold.adapter.in.web.response.RegularStockHoldResponse;
import org.springframework.http.ResponseEntity;

public interface RegularStockHoldApi {
    ResponseEntity<ApiResponse<RegularStockHoldResponse>> create(CreateRegularStockHoldRequest request);
}
