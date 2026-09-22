package com.philia.flashsale.product.catalogadmin.application.usecase;

import com.philia.flashsale.product.catalogadmin.application.port.in.LookupAdminVariantDisplaysUseCase;
import com.philia.flashsale.product.catalogadmin.application.port.out.LoadAdminVariantDisplaysPort;
import com.philia.flashsale.product.catalogadmin.application.query.LookupAdminVariantDisplaysQuery;
import com.philia.flashsale.product.catalogadmin.application.result.AdminVariantDisplayResult;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminVariantDisplayQueryService implements LookupAdminVariantDisplaysUseCase {
    private final LoadAdminVariantDisplaysPort loadAdminVariantDisplays;

    public AdminVariantDisplayQueryService(LoadAdminVariantDisplaysPort loadAdminVariantDisplays) {
        this.loadAdminVariantDisplays = loadAdminVariantDisplays;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminVariantDisplayResult> lookup(LookupAdminVariantDisplaysQuery query) {
        return loadAdminVariantDisplays.load(query.variantIds());
    }
}
