package com.supermarket.pos.categories.service;

import java.util.Map;

/**
 * How many products use each category. Declared here and implemented by the products
 * module, so categories never depends on products (products already depends on categories).
 */
public interface CategoryUsage {

    /** category id → number of active products directly in that category. */
    Map<Long, Long> activeProductCounts();

    long activeProductCount(Long categoryId);
}
