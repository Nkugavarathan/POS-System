package com.supermarket.pos.products.service;

import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.money.MoneyPolicy;
import com.supermarket.pos.products.entity.Product;

import java.math.BigDecimal;
import java.util.Map;

/** Quantity validation by unit of measure: positive, at most 3 decimals, whole numbers for discrete units. */
public final class QuantityRules {

    private QuantityRules() {
    }

    public static BigDecimal validate(Product product, BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Quantity for " + product.getName() + " must be greater than zero.",
                    Map.of("productId", product.getId()));
        }
        BigDecimal stripped = quantity.stripTrailingZeros();
        if (stripped.scale() > MoneyPolicy.QUANTITY_SCALE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Quantity for " + product.getName()
                    + " can have at most 3 decimal places.", Map.of("productId", product.getId()));
        }
        if (!product.getUnitOfMeasure().isFractional() && stripped.scale() > 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, product.getName() + " is sold per "
                    + product.getUnitOfMeasure().name().toLowerCase() + "; enter a whole number.",
                    Map.of("productId", product.getId()));
        }
        if (quantity.compareTo(new BigDecimal("100000")) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Quantity for " + product.getName() + " is too large.",
                    Map.of("productId", product.getId()));
        }
        return MoneyPolicy.quantity(quantity);
    }
}
