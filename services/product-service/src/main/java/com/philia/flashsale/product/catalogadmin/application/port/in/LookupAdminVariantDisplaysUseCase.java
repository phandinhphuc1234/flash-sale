package com.philia.flashsale.product.catalogadmin.application.port.in;

import com.philia.flashsale.product.catalogadmin.application.query.LookupAdminVariantDisplaysQuery;
import com.philia.flashsale.product.catalogadmin.application.result.AdminVariantDisplayResult;
import java.util.List;

public interface LookupAdminVariantDisplaysUseCase {
    List<AdminVariantDisplayResult> lookup(LookupAdminVariantDisplaysQuery query);
}
