package com.philia.flashsale.product.application.port.out;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface LoadExistingCategoriesPort {

    Set<UUID> existingCategoryIds(Collection<UUID> requestedCategoryIds);
}
