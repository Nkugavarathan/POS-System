-- V9: returns and refunds. Always linked to the original sale; completed sales are never edited
-- to hide mistakes (only returned quantities/amounts accumulate, bounded by CHECK constraints in V8).

CREATE TABLE returns (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    return_number    VARCHAR(40)   NOT NULL,
    store_id         BIGINT        NOT NULL,
    register_id      BIGINT        NOT NULL,
    shift_id         BIGINT        NOT NULL,
    original_sale_id BIGINT        NOT NULL,
    processed_by     BIGINT        NOT NULL,
    approved_by      BIGINT        NULL,
    reason_code      VARCHAR(40)   NOT NULL,
    notes            VARCHAR(500)  NULL,
    refund_total     DECIMAL(15,2) NOT NULL,
    tax_total        DECIMAL(15,2) NOT NULL,
    loyalty_points_reversed BIGINT NOT NULL DEFAULT 0,
    created_at       DATETIME(6)   NOT NULL,
    CONSTRAINT pk_returns PRIMARY KEY (id),
    CONSTRAINT uk_returns_number UNIQUE (return_number),
    CONSTRAINT fk_ret_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_ret_shift FOREIGN KEY (shift_id) REFERENCES cashier_shifts (id),
    CONSTRAINT fk_ret_sale FOREIGN KEY (original_sale_id) REFERENCES sales (id),
    CONSTRAINT fk_ret_user FOREIGN KEY (processed_by) REFERENCES users (id),
    CONSTRAINT ck_ret_total CHECK (refund_total > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_returns_sale ON returns (original_sale_id);
CREATE INDEX ix_returns_store_time ON returns (store_id, created_at);
CREATE INDEX ix_returns_shift ON returns (shift_id);

CREATE TABLE return_items (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    return_id      BIGINT        NOT NULL,
    sale_item_id   BIGINT        NOT NULL,
    product_id     BIGINT        NOT NULL,
    quantity       DECIMAL(15,3) NOT NULL,
    refund_amount  DECIMAL(15,2) NOT NULL,
    tax_amount     DECIMAL(15,2) NOT NULL,
    restock        BOOLEAN       NOT NULL,
    item_condition VARCHAR(20)   NOT NULL,
    reason         VARCHAR(255)  NULL,
    CONSTRAINT pk_return_items PRIMARY KEY (id),
    CONSTRAINT fk_ri_return FOREIGN KEY (return_id) REFERENCES returns (id),
    CONSTRAINT fk_ri_sale_item FOREIGN KEY (sale_item_id) REFERENCES sale_items (id),
    CONSTRAINT ck_ri_qty CHECK (quantity > 0),
    CONSTRAINT ck_ri_amount CHECK (refund_amount >= 0),
    CONSTRAINT ck_ri_condition CHECK (item_condition IN ('SALEABLE', 'DAMAGED', 'EXPIRED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE refunds (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    return_id    BIGINT        NOT NULL,
    tender_type  VARCHAR(20)   NOT NULL,
    amount       DECIMAL(15,2) NOT NULL,
    reference    VARCHAR(80)   NULL,
    card_last4   CHAR(4)       NULL,
    loyalty_points BIGINT      NULL,
    processed_by BIGINT        NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    CONSTRAINT pk_refunds PRIMARY KEY (id),
    CONSTRAINT fk_refund_return FOREIGN KEY (return_id) REFERENCES returns (id),
    CONSTRAINT fk_refund_tender FOREIGN KEY (tender_type) REFERENCES tender_types (code),
    CONSTRAINT ck_refund_amount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_refunds_return ON refunds (return_id);
