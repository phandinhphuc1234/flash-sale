package com.philia.flashsale.product.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface CategoryReadJpaRepository extends JpaRepository<CategoryJpaEntity, UUID> {

    boolean existsBySlug(String slug);

    List<CategoryJpaEntity> findByParentIdIsNullAndStatusOrderBySortOrderAscIdAsc(String status);

    List<CategoryJpaEntity> findByParentIdAndStatusOrderBySortOrderAscIdAsc(UUID parentId, String status);
}
