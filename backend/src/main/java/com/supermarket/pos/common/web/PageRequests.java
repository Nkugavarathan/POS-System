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
            for (String part : raw.split(",")) {
                String token = part.trim();
                if (token.isEmpty()) {
                    continue;
                }
                // Spring splits a single "name,desc" parameter into ["name", "desc"]: a bare
                // direction applies to the field before it.
                if (token.equalsIgnoreCase("asc") || token.equalsIgnoreCase("desc")) {
                    if (!orders.isEmpty()) {
                        Sort.Order last = orders.remove(orders.size() - 1);
                        orders.add(last.with(Sort.Direction.fromString(token)));
                    }
                    continue;
                }
                String property = allowed.get(token);
                if (property == null) {
                    throw new ApiException(ErrorCode.INVALID_SORT_FIELD, "Sorting by '" + token + "' is not supported.",
                            Map.of("allowed", allowed.keySet()));
                }
                orders.add(Sort.Order.asc(property));
            }
        }
        return PageRequest.of(p, s, orders.isEmpty() ? fallback : Sort.by(orders));
    }
}
