package com.philia.flashsale.order.regularpurchase.application.port.out;

import com.philia.flashsale.order.regularpurchase.application.model.CartCheckoutSnapshot;
import java.util.UUID;

public interface LoadCartCheckoutSnapshotPort {
    CartCheckoutSnapshot load(UUID shopperId, String traceId);
}
