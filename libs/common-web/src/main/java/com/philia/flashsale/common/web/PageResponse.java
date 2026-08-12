package com.philia.flashsale.common.web;

import java.util.List;

/**
 * Shared HTTP payload shape for bounded paginated results.
 *
 * @param data items in the current page
 * @param page pagination metadata
 * @param <T>     item type
 */
public record PageResponse<T>(
        List<T> data,
        PageMeta page
) {

    public PageResponse {
        data = data == null ? List.of() : List.copyOf(data);
        page = page == null ? PageMeta.of(0, 10, 0) : page;
    }

    public static <T> PageResponse<T> of(List<T> data, PageMeta page) {
        return new PageResponse<>(data, page);
    }

    public static <T> PageResponse<T> of(List<T> data, int number, int size, long totalElements) {
        return new PageResponse<>(data, PageMeta.of(number, size, totalElements));
    }
}
