package com.supermarket.pos.products.entity;

/** How a product is counted. Discrete units must be sold in whole numbers. */
public enum UnitOfMeasure {
    EACH(false), PACK(false), BOTTLE(false), KG(true), G(true), L(true), ML(true);

    private final boolean fractional;

    UnitOfMeasure(boolean fractional) {
        this.fractional = fractional;
    }

    public boolean isFractional() {
        return fractional;
    }
}
