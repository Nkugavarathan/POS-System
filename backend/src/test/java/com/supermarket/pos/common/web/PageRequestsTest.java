package com.supermarket.pos.common.web;

import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageRequestsTest {

    private static final Map<String, String> ALLOWED = Map.of("name", "name", "price", "sellingPrice");

    @Test
    void appliesDefaultsAndCapsPageSize() {
        Pageable p = PageRequests.of(-3, 5000, null, ALLOWED, Sort.by("name"));
        assertThat(p.getPageNumber()).isZero();
        assertThat(p.getPageSize()).isEqualTo(PageRequests.MAX_SIZE);
        assertThat(p.getSort()).isEqualTo(Sort.by("name"));
    }

    @Test
    void mapsWhitelistedSortToEntityProperty() {
        Pageable p = PageRequests.of(2, 10, List.of("price,desc"), ALLOWED, Sort.unsorted());
        assertThat(p.getSort().getOrderFor("sellingPrice")).isNotNull();
        assertThat(p.getSort().getOrderFor("sellingPrice").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void rejectsUnknownSortField() {
        assertThatThrownBy(() -> PageRequests.of(0, 10, List.of("passwordHash,asc"), ALLOWED, Sort.unsorted()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCode.INVALID_SORT_FIELD);
    }
}
