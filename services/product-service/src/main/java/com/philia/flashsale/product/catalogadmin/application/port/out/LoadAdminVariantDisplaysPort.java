package com.philia.flashsale.product.catalogadmin.application.port.out;

import com.philia.flashsale.product.catalogadmin.application.result.AdminVariantDisplayResult;
import java.util.List;
import java.util.UUID;

public interface LoadAdminVariantDisplaysPort {
    List<AdminVariantDisplayResult> load(List<UUID> variantIds);
}
