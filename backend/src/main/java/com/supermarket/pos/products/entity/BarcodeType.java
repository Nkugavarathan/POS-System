package com.supermarket.pos.products.entity;

public enum BarcodeType {
    EAN_13, EAN_8, UPC_A, UPC_E, GTIN_14, CODE_128,
    /** Store-assigned numeric codes (e.g. short PLUs) without a GS1 check digit. */
    INTERNAL
}
