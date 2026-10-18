package com.supermarket.pos.common.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** The single pagination envelope used by every list endpoint. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<SortOrder> sort) {

    public record SortOrder(String property, String direction) {
    }

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return from(page.map(mapper));
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        List<SortOrder> sort = page.getSort().stream()
                .map(o -> new SortOrder(o.getProperty(), o.getDirection().name()))
                .toList();
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), sort);
    }
}
