package com.philia.flashsale.cart.application.port.out;

import com.philia.flashsale.cart.application.result.ProductDisplayBatch;
import java.util.List;
import java.util.UUID;

/** Cart-facing capability for one ordered Product display lookup batch. */
public interface LoadProductDisplaysPort {

    ProductDisplayBatch load(List<UUID> variantIds, String traceId);
}
