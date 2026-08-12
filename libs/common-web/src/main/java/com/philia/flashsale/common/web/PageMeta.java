package com.philia.flashsale.common.web;

/**
 * Shared HTTP metadata for bounded paginated responses.
 *
 * @param number        zero-based page index
 * @param size          requested page size
 * @param totalElements total number of matching elements
 * @param totalPages    total number of pages for the requested size
 * @param hasNext       whether another page is available
 */
public record PageMeta(
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public PageMeta {
        if (number < 0) {
            throw new IllegalArgumentException("number must be zero or positive");
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

    public static PageMeta of(int number, int size, long totalElements) {
        int totalPages = totalElements == 0 ? 0 : Math.toIntExact((totalElements + size - 1) / size);
        boolean hasNext = totalPages > 0 && number < totalPages - 1;
        return new PageMeta(number, size, totalElements, totalPages, hasNext);
    }
}
