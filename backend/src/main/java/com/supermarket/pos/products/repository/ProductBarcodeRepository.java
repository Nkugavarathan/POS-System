package com.supermarket.pos.products.repository;

import com.supermarket.pos.products.entity.ProductBarcode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductBarcodeRepository extends JpaRepository<ProductBarcode, Long> {

    /** POS lookup: one query returns the barcode, its product and the product's tax rate. */
    @Query("""
            select b from ProductBarcode b
            join fetch b.product p
            join fetch p.taxRate
            join fetch p.category
            where b.barcode = :barcode
            """)
    Optional<ProductBarcode> findForLookup(@Param("barcode") String barcode);

    boolean existsByBarcode(String barcode);

    List<ProductBarcode> findByBarcodeIn(Collection<String> barcodes);
}
