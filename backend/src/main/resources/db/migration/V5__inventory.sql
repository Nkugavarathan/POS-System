-- V5: inventory ledger. Stock levels are never edited directly: every change is a
-- stock_movements row written in the same transaction as the balance update.

CREATE TABLE inventory_balances (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    store_id         BIGINT        NOT NULL,
    product_id       BIGINT        NOT NULL,
    quantity_on_hand DECIMAL(15,3) NOT NULL DEFAULT 0,
    average_cost     DECIMAL(15,4) NOT NULL DEFAULT 0,
    last_movement_at DATETIME(6)   NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_inventory_balances PRIMARY KEY (id),
    CONSTRAINT uk_ib_store_product UNIQUE (store_id, product_id),
    CONSTRAINT fk_ib_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_ib_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_ib_nonneg CHECK (quantity_on_hand >= 0),
    CONSTRAINT ck_ib_cost CHECK (average_cost >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE inventory_batches (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    store_id              BIGINT        NOT NULL,
    product_id            BIGINT        NOT NULL,
    batch_number          VARCHAR(50)   NOT NULL,
    expiry_date           DATE          NULL,
    received_at           DATETIME(6)   NOT NULL,
    unit_cost             DECIMAL(15,4) NOT NULL,
    quantity_received     DECIMAL(15,3) NOT NULL,
    quantity_remaining    DECIMAL(15,3) NOT NULL,
    goods_receipt_item_id BIGINT        NULL,
    status                VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT pk_inventory_batches PRIMARY KEY (id),
    CONSTRAINT uk_batch UNIQUE (store_id, product_id, batch_number),
    CONSTRAINT fk_batch_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_batch_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_batch_nonneg CHECK (quantity_remaining >= 0 AND quantity_received >= 0),
    CONSTRAINT ck_batch_status CHECK (status IN ('ACTIVE', 'DEPLETED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_batch_fefo ON inventory_batches (store_id, product_id, status, expiry_date, received_at);
CREATE INDEX ix_batch_expiry ON inventory_batches (store_id, status, expiry_date);

CREATE TABLE stock_movements (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    store_id          BIGINT        NOT NULL,
    product_id        BIGINT        NOT NULL,
    batch_id          BIGINT        NULL,
    batch_key         BIGINT AS (COALESCE(batch_id, 0)) STORED,
    movement_type     VARCHAR(30)   NOT NULL,
    quantity_change   DECIMAL(15,3) NOT NULL,
    quantity_before   DECIMAL(15,3) NOT NULL,
    quantity_after    DECIMAL(15,3) NOT NULL,
    unit_cost         DECIMAL(15,4) NULL,
    reference_type    VARCHAR(30)   NOT NULL,
    reference_id      BIGINT        NULL,
    reference_line_id BIGINT        NULL,
    reason            VARCHAR(255)  NULL,
    performed_by      BIGINT        NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    CONSTRAINT pk_stock_movements PRIMARY KEY (id),
    CONSTRAINT fk_sm_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_sm_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_sm_batch FOREIGN KEY (batch_id) REFERENCES inventory_batches (id),
    CONSTRAINT fk_sm_user FOREIGN KEY (performed_by) REFERENCES users (id),
    -- A document line can post each movement type to each batch only once (no double deduction).
    CONSTRAINT uk_sm_doc_line UNIQUE (reference_type, reference_id, reference_line_id, batch_key, movement_type),
    CONSTRAINT ck_sm_type CHECK (movement_type IN ('OPENING_BALANCE', 'PURCHASE_RECEIPT', 'SALE', 'SALE_RETURN',
        'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'WRITE_OFF_DAMAGED', 'WRITE_OFF_EXPIRED', 'COUNT_CORRECTION',
        'TRANSFER_OUT', 'TRANSFER_IN')),
    CONSTRAINT ck_sm_nonzero CHECK (quantity_change <> 0),
    CONSTRAINT ck_sm_math CHECK (quantity_after = quantity_before + quantity_change),
    CONSTRAINT ck_sm_after CHECK (quantity_after >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_sm_store_product_time ON stock_movements (store_id, product_id, created_at);
CREATE INDEX ix_sm_reference ON stock_movements (reference_type, reference_id);
CREATE INDEX ix_sm_type_time ON stock_movements (store_id, movement_type, created_at);

CREATE TABLE stock_adjustments (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    adjustment_number VARCHAR(40)   NOT NULL,
    store_id          BIGINT        NOT NULL,
    adjustment_type   VARCHAR(30)   NOT NULL,
    reason            VARCHAR(255)  NOT NULL,
    total_value       DECIMAL(15,2) NOT NULL DEFAULT 0,
    created_by        BIGINT        NOT NULL,
    approved_by       BIGINT        NULL,
    created_at        DATETIME(6)   NOT NULL,
    CONSTRAINT pk_stock_adjustments PRIMARY KEY (id),
    CONSTRAINT uk_adj_number UNIQUE (adjustment_number),
    CONSTRAINT fk_adj_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_adj_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_adj_approved_by FOREIGN KEY (approved_by) REFERENCES users (id),
    CONSTRAINT ck_adj_type CHECK (adjustment_type IN ('OPENING_BALANCE', 'ADJUSTMENT', 'WRITE_OFF_DAMAGED', 'WRITE_OFF_EXPIRED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_adj_store_time ON stock_adjustments (store_id, created_at);

CREATE TABLE stock_adjustment_items (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    adjustment_id   BIGINT        NOT NULL,
    product_id      BIGINT        NOT NULL,
    batch_id        BIGINT        NULL,
    quantity_change DECIMAL(15,3) NOT NULL,
    unit_cost       DECIMAL(15,4) NOT NULL DEFAULT 0,
    reason          VARCHAR(255)  NULL,
    CONSTRAINT pk_stock_adjustment_items PRIMARY KEY (id),
    CONSTRAINT fk_adj_item_adj FOREIGN KEY (adjustment_id) REFERENCES stock_adjustments (id),
    CONSTRAINT fk_adj_item_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_adj_item_batch FOREIGN KEY (batch_id) REFERENCES inventory_batches (id),
    CONSTRAINT ck_adj_item_nonzero CHECK (quantity_change <> 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE stock_counts (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    count_number VARCHAR(40)  NOT NULL,
    store_id     BIGINT       NOT NULL,
    category_id  BIGINT       NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    notes        VARCHAR(255) NULL,
    created_by   BIGINT       NOT NULL,
    submitted_by BIGINT       NULL,
    posted_by    BIGINT       NULL,
    created_at   DATETIME(6)  NOT NULL,
    submitted_at DATETIME(6)  NULL,
    posted_at    DATETIME(6)  NULL,
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_stock_counts PRIMARY KEY (id),
    CONSTRAINT uk_count_number UNIQUE (count_number),
    CONSTRAINT fk_count_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_count_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT ck_count_status CHECK (status IN ('OPEN', 'SUBMITTED', 'POSTED', 'CANCELLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE stock_count_items (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    stock_count_id   BIGINT        NOT NULL,
    product_id       BIGINT        NOT NULL,
    system_quantity  DECIMAL(15,3) NOT NULL,
    counted_quantity DECIMAL(15,3) NULL,
    quantity_at_post DECIMAL(15,3) NULL,
    variance         DECIMAL(15,3) NULL,
    CONSTRAINT pk_stock_count_items PRIMARY KEY (id),
    CONSTRAINT uk_count_item UNIQUE (stock_count_id, product_id),
    CONSTRAINT fk_count_item_count FOREIGN KEY (stock_count_id) REFERENCES stock_counts (id),
    CONSTRAINT fk_count_item_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_count_item_qty CHECK (counted_quantity IS NULL OR counted_quantity >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE stock_alerts (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    store_id        BIGINT       NOT NULL,
    product_id      BIGINT       NOT NULL,
    batch_id        BIGINT       NULL,
    alert_type      VARCHAR(20)  NOT NULL,
    message         VARCHAR(255) NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    acknowledged_by BIGINT       NULL,
    acknowledged_at DATETIME(6)  NULL,
    CONSTRAINT pk_stock_alerts PRIMARY KEY (id),
    CONSTRAINT fk_alert_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_alert_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_alert_batch FOREIGN KEY (batch_id) REFERENCES inventory_batches (id),
    CONSTRAINT ck_alert_type CHECK (alert_type IN ('LOW_STOCK', 'EXPIRING', 'EXPIRED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_alerts_open ON stock_alerts (store_id, acknowledged_at, alert_type);
CREATE INDEX ix_alerts_product ON stock_alerts (store_id, product_id, alert_type, acknowledged_at);
