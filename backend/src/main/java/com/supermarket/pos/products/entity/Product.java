package com.supermarket.pos.products.entity;

import com.supermarket.pos.categories.entity.Category;
import com.supermarket.pos.common.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Catalog item. Stock is not stored here: it lives in the per-store inventory ledger
 * (inventory_balances). {@code costPrice} is the standard/last cost used as the default when
 * receiving stock; the inventory ledger keeps the moving average cost per store.
 */
@Getter
@Setter
@Entity
@Table(name = "products")
public class Product extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String sku;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(length = 80)
    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_of_measure", nullable = false, length = 10)
    private UnitOfMeasure unitOfMeasure;

    @Column(name = "cost_price", nullable = false, precision = 15, scale = 4)
    private BigDecimal costPrice;

    @Column(name = "selling_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal sellingPrice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_rate_id", nullable = false)
    private TaxRate taxRate;

    @Column(name = "reorder_level", nullable = false, precision = 15, scale = 3)
    private BigDecimal reorderLevel;

    @Column(name = "track_batches", nullable = false)
    private boolean trackBatches;

    @Column(name = "track_expiry", nullable = false)
    private boolean trackExpiry;

    @Column(name = "allow_price_override", nullable = false)
    private boolean allowPriceOverride;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by")
    private Long createdBy;

    @Version
    private long version;

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProductBarcode> barcodes = new LinkedHashSet<>();

    public Optional<ProductBarcode> primaryBarcode() {
        return barcodes.stream().filter(ProductBarcode::isPrimary).findFirst()
                .or(() -> barcodes.stream().findFirst());
    }

    public void addBarcode(ProductBarcode barcode) {
        barcode.setProduct(this);
        barcodes.add(barcode);
    }
}
