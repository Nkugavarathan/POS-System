package com.supermarket.pos.common.web;

import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link Pageable} from untrusted query parameters. Sort fields are whitelisted
 * (API name → entity property) so clients can never sort by arbitrary or sensitive columns.
 */
public final class PageRequests {

    public static final int MAX_SIZE = 100;
    public static final int DEFAULT_SIZE = 20;

    private PageRequests() {
    }

    /**
     * @param sortParams values such as {@code "name,asc"} or {@code "updatedAt,desc"}
     * @param allowed    API sort name → JPA property path
     * @param fallback   default sort when the client gives none
     */
    public static Pageable of(Integer page, Integer size, List<String> sortParams,
                              Map<String, String> allowed, Sort fallback) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        if (sortParams == null || sortParams.isEmpty()) {
            return PageRequest.of(p, s, fallback);
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (String raw : sortParams) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String[] parts = raw.split(",");
            String field = parts[0].trim();
            String property = allowed.get(field);
            if (property == null) {
                throw new ApiException(ErrorCode.INVALID_SORT_FIELD, "Sorting by '" + field + "' is not supported.",
                        Map.of("allowed", allowed.keySet()));
            }
            Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                    ? Sort.Direction.DESC : Sort.Direction.ASC;
            orders.add(new Sort.Order(direction, property));
        }
        return PageRequest.of(p, s, orders.isEmpty() ? fallback : Sort.by(orders));
    }
}
