package com.philia.flashsale.common.web;

/**
 * Shared HTTP metadata for bounded paginated responses.
 *
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total number of matching elements
 * @param totalPages    total number of pages for the requested size
 * @param first         whether this is the first page
 * @param last          whether this is the last page
 */
public record PageMeta(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public PageMeta {
        if (page < 0) {
            throw new IllegalArgumentException("page must be zero or positive");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be greater than zero");
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must be zero or positive");
        }
        if (totalPages < 0) {
            throw new IllegalArgumentException("totalPages must be zero or positive");
        }
    }

    public static PageMeta of(int page, int size, long totalElements) {
        int totalPages = totalElements == 0 ? 0 : Math.toIntExact((totalElements + size - 1) / size);
        boolean first = page == 0;
        boolean last = totalPages == 0 || page >= totalPages - 1;
        return new PageMeta(page, size, totalElements, totalPages, first, last);
    }
}
