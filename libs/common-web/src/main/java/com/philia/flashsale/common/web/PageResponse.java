package com.philia.flashsale.common.web;

import java.util.List;

/**
 * Shared HTTP payload shape for bounded paginated results.
 *
 * @param content items in the current page
 * @param meta    pagination metadata
 * @param <T>     item type
 */
public record PageResponse<T>(
        List<T> content,
        PageMeta meta
) {

    public PageResponse {
        content = content == null ? List.of() : List.copyOf(content);
        meta = meta == null ? PageMeta.of(0, 10, 0) : meta;
    }

    public static <T> PageResponse<T> of(List<T> content, PageMeta meta) {
        return new PageResponse<>(content, meta);
    }

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        return new PageResponse<>(content, PageMeta.of(page, size, totalElements));
    }
}
