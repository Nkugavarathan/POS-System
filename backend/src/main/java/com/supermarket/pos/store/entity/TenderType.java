package com.supermarket.pos.store.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Configurable payment method (CASH, CARD, LOYALTY, ...). */
@Getter
@Setter
@Entity
@Table(name = "tender_types")
public class TenderType {

    public static final String CASH = "CASH";
    public static final String CARD = "CARD";
    public static final String LOYALTY = "LOYALTY";

    @Id
    @Column(length = 20)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "is_cash", nullable = false)
    private boolean cash;

    @Column(name = "requires_reference", nullable = false)
    private boolean requiresReference;

    @Column(name = "allows_change", nullable = false)
    private boolean allowsChange;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
