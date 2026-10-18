-- V10: suppliers, purchase orders and goods receipts (GRNs).

CREATE TABLE suppliers (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    code                VARCHAR(20)  NOT NULL,
    name                VARCHAR(150) NOT NULL,
    contact_person      VARCHAR(120) NULL,
    phone               VARCHAR(30)  NULL,
    email               VARCHAR(120) NULL,
    address             VARCHAR(300) NULL,
    tax_registration_no VARCHAR(40)  NULL,
    payment_terms_days  INT          NOT NULL DEFAULT 30,
    notes               VARCHAR(500) NULL,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_suppliers PRIMARY KEY (id),
    CONSTRAINT uk_suppliers_code UNIQUE (code),
    CONSTRAINT ck_supplier_terms CHECK (payment_terms_days >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_suppliers_name ON suppliers (name);

CREATE TABLE purchase_orders (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    po_number      VARCHAR(40)   NOT NULL,
    store_id       BIGINT        NOT NULL,
    supplier_id    BIGINT        NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    order_date     DATE          NULL,
    expected_date  DATE          NULL,
    expected_total DECIMAL(15,2) NOT NULL DEFAULT 0,
    notes          VARCHAR(500)  NULL,
    created_by     BIGINT        NOT NULL,
    approved_by    BIGINT        NULL,
    approved_at    DATETIME(6)   NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_purchase_orders PRIMARY KEY (id),
    CONSTRAINT uk_po_number UNIQUE (po_number),
    CONSTRAINT fk_po_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_po_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT ck_po_status CHECK (status IN ('DRAFT', 'ORDERED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED', 'CANCELLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_po_supplier_status ON purchase_orders (supplier_id, status);
CREATE INDEX ix_po_store_status ON purchase_orders (store_id, status, created_at);

CREATE TABLE purchase_order_items (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    purchase_order_id  BIGINT        NOT NULL,
    product_id         BIGINT        NOT NULL,
    quantity_ordered   DECIMAL(15,3) NOT NULL,
    quantity_received  DECIMAL(15,3) NOT NULL DEFAULT 0,
    expected_unit_cost DECIMAL(15,4) NOT NULL,
    CONSTRAINT pk_purchase_order_items PRIMARY KEY (id),
    CONSTRAINT uk_poi UNIQUE (purchase_order_id, product_id),
    CONSTRAINT fk_poi_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id),
    CONSTRAINT fk_poi_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_poi_qty CHECK (quantity_ordered > 0 AND quantity_received >= 0 AND quantity_received <= quantity_ordered),
    CONSTRAINT ck_poi_cost CHECK (expected_unit_cost >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE goods_receipts (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    grn_number          VARCHAR(40)   NOT NULL,
    store_id            BIGINT        NOT NULL,
    supplier_id         BIGINT        NOT NULL,
    purchase_order_id   BIGINT        NULL,
    supplier_invoice_no VARCHAR(60)   NULL,
    received_at         DATETIME(6)   NOT NULL,
    received_by         BIGINT        NOT NULL,
    total_cost          DECIMAL(15,2) NOT NULL,
    notes               VARCHAR(500)  NULL,
    CONSTRAINT pk_goods_receipts PRIMARY KEY (id),
    CONSTRAINT uk_grn_number UNIQUE (grn_number),
    CONSTRAINT fk_grn_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_grn_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_grn_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_grn_po ON goods_receipts (purchase_order_id);
CREATE INDEX ix_grn_supplier_time ON goods_receipts (supplier_id, received_at);

CREATE TABLE goods_receipt_items (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    goods_receipt_id       BIGINT        NOT NULL,
    purchase_order_item_id BIGINT        NULL,
    product_id             BIGINT        NOT NULL,
    quantity_received      DECIMAL(15,3) NOT NULL,
    unit_cost              DECIMAL(15,4) NOT NULL,
    batch_number           VARCHAR(50)   NULL,
    expiry_date            DATE          NULL,
    batch_id               BIGINT        NULL,
    CONSTRAINT pk_goods_receipt_items PRIMARY KEY (id),
    CONSTRAINT fk_gri_grn FOREIGN KEY (goods_receipt_id) REFERENCES goods_receipts (id),
    CONSTRAINT fk_gri_poi FOREIGN KEY (purchase_order_item_id) REFERENCES purchase_order_items (id),
    CONSTRAINT fk_gri_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_gri_batch FOREIGN KEY (batch_id) REFERENCES inventory_batches (id),
    CONSTRAINT ck_gri_qty CHECK (quantity_received > 0),
    CONSTRAINT ck_gri_cost CHECK (unit_cost >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
