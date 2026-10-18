-- V8: sales, sale lines, discount records and payments. A sale row exists only once it is fully
-- paid; afterwards only status and returned quantities change (corrections are returns).

CREATE TABLE sales (
    id                         BIGINT        NOT NULL AUTO_INCREMENT,
    receipt_number             VARCHAR(50)   NOT NULL,
    store_id                   BIGINT        NOT NULL,
    register_id                BIGINT        NOT NULL,
    shift_id                   BIGINT        NOT NULL,
    cashier_id                 BIGINT        NOT NULL,
    customer_id                BIGINT        NULL,
    channel                    VARCHAR(10)   NOT NULL DEFAULT 'POS',
    online_order_id            BIGINT        NULL,
    status                     VARCHAR(20)   NOT NULL DEFAULT 'COMPLETED',
    currency                   CHAR(3)       NOT NULL,
    prices_include_tax         BOOLEAN       NOT NULL,
    subtotal                   DECIMAL(15,2) NOT NULL,
    line_discount_total        DECIMAL(15,2) NOT NULL,
    transaction_discount_total DECIMAL(15,2) NOT NULL,
    discount_total             DECIMAL(15,2) NOT NULL,
    tax_total                  DECIMAL(15,2) NOT NULL,
    grand_total                DECIMAL(15,2) NOT NULL,
    amount_paid                DECIMAL(15,2) NOT NULL,
    amount_tendered            DECIMAL(15,2) NOT NULL,
    change_due                 DECIMAL(15,2) NOT NULL,
    item_count                 INT           NOT NULL,
    cost_total                 DECIMAL(15,2) NOT NULL,
    refunded_total             DECIMAL(15,2) NOT NULL DEFAULT 0,
    loyalty_points_earned      BIGINT        NOT NULL DEFAULT 0,
    loyalty_points_redeemed    BIGINT        NOT NULL DEFAULT 0,
    reprint_count              INT           NOT NULL DEFAULT 0,
    completed_at               DATETIME(6)   NOT NULL,
    version                    BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_sales PRIMARY KEY (id),
    CONSTRAINT uk_sales_receipt UNIQUE (receipt_number),
    CONSTRAINT fk_sales_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_sales_register FOREIGN KEY (register_id) REFERENCES registers (id),
    CONSTRAINT fk_sales_shift FOREIGN KEY (shift_id) REFERENCES cashier_shifts (id),
    CONSTRAINT fk_sales_cashier FOREIGN KEY (cashier_id) REFERENCES users (id),
    CONSTRAINT fk_sales_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT ck_sales_status CHECK (status IN ('COMPLETED', 'PARTIALLY_RETURNED', 'FULLY_RETURNED')),
    CONSTRAINT ck_sales_channel CHECK (channel IN ('POS', 'ONLINE')),
    CONSTRAINT ck_sales_totals CHECK (grand_total >= 0 AND amount_paid = grand_total AND change_due >= 0
        AND amount_tendered = amount_paid + change_due AND refunded_total >= 0 AND refunded_total <= grand_total)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_sales_store_completed ON sales (store_id, completed_at);
CREATE INDEX ix_sales_cashier_completed ON sales (cashier_id, completed_at);
CREATE INDEX ix_sales_shift ON sales (shift_id);
CREATE INDEX ix_sales_customer ON sales (customer_id);

CREATE TABLE sale_items (
    id                            BIGINT        NOT NULL AUTO_INCREMENT,
    sale_id                       BIGINT        NOT NULL,
    line_number                   INT           NOT NULL,
    product_id                    BIGINT        NOT NULL,
    sku_snapshot                  VARCHAR(40)   NOT NULL,
    barcode_snapshot              VARCHAR(32)   NULL,
    product_name_snapshot         VARCHAR(150)  NOT NULL,
    category_id                   BIGINT        NOT NULL,
    unit_of_measure               VARCHAR(10)   NOT NULL,
    quantity                      DECIMAL(15,3) NOT NULL,
    unit_price                    DECIMAL(15,2) NOT NULL,
    original_unit_price           DECIMAL(15,2) NOT NULL,
    price_override_by             BIGINT        NULL,
    gross_amount                  DECIMAL(15,2) NOT NULL,
    promo_discount_amount         DECIMAL(15,2) NOT NULL,
    manual_discount_amount        DECIMAL(15,2) NOT NULL,
    allocated_txn_discount_amount DECIMAL(15,2) NOT NULL,
    net_amount                    DECIMAL(15,2) NOT NULL,
    tax_rate_percent              DECIMAL(6,3)  NOT NULL,
    tax_amount                    DECIMAL(15,2) NOT NULL,
    line_total                    DECIMAL(15,2) NOT NULL,
    unit_cost                     DECIMAL(15,4) NOT NULL,
    cost_amount                   DECIMAL(15,2) NOT NULL,
    quantity_returned             DECIMAL(15,3) NOT NULL DEFAULT 0,
    refunded_amount               DECIMAL(15,2) NOT NULL DEFAULT 0,
    CONSTRAINT pk_sale_items PRIMARY KEY (id),
    CONSTRAINT uk_si_sale_line UNIQUE (sale_id, line_number),
    CONSTRAINT fk_si_sale FOREIGN KEY (sale_id) REFERENCES sales (id),
    CONSTRAINT fk_si_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_si_qty CHECK (quantity > 0),
    CONSTRAINT ck_si_returned CHECK (quantity_returned >= 0 AND quantity_returned <= quantity),
    CONSTRAINT ck_si_refunded CHECK (refunded_amount >= 0 AND refunded_amount <= line_total),
    CONSTRAINT ck_si_amounts CHECK (gross_amount >= 0 AND net_amount >= 0 AND tax_amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_si_product ON sale_items (product_id);

CREATE TABLE sale_discounts (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    sale_id      BIGINT        NOT NULL,
    sale_item_id BIGINT        NULL,
    source       VARCHAR(30)   NOT NULL,
    method       VARCHAR(10)   NOT NULL,
    value        DECIMAL(15,2) NOT NULL,
    amount       DECIMAL(15,2) NOT NULL,
    reason       VARCHAR(255)  NULL,
    promotion_id BIGINT        NULL,
    applied_by   BIGINT        NOT NULL,
    approved_by  BIGINT        NULL,
    CONSTRAINT pk_sale_discounts PRIMARY KEY (id),
    CONSTRAINT fk_sd_sale FOREIGN KEY (sale_id) REFERENCES sales (id),
    CONSTRAINT fk_sd_item FOREIGN KEY (sale_item_id) REFERENCES sale_items (id),
    CONSTRAINT ck_sd_source CHECK (source IN ('PROMOTION', 'MANUAL_LINE', 'MANUAL_TRANSACTION', 'PRICE_OVERRIDE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_sd_sale ON sale_discounts (sale_id);

CREATE TABLE payments (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    sale_id            BIGINT        NOT NULL,
    tender_type        VARCHAR(20)   NOT NULL,
    amount             DECIMAL(15,2) NOT NULL,
    amount_tendered    DECIMAL(15,2) NOT NULL,
    change_given       DECIMAL(15,2) NOT NULL DEFAULT 0,
    status             VARCHAR(20)   NOT NULL DEFAULT 'CAPTURED',
    provider           VARCHAR(40)   NOT NULL,
    provider_reference VARCHAR(80)   NULL,
    card_brand         VARCHAR(20)   NULL,
    card_last4         CHAR(4)       NULL,
    auth_code          VARCHAR(20)   NULL,
    loyalty_points     BIGINT        NULL,
    recorded_by        BIGINT        NOT NULL,
    created_at         DATETIME(6)   NOT NULL,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT fk_pay_sale FOREIGN KEY (sale_id) REFERENCES sales (id),
    CONSTRAINT fk_pay_tender FOREIGN KEY (tender_type) REFERENCES tender_types (code),
    CONSTRAINT ck_pay_amount CHECK (amount > 0 AND amount_tendered >= amount AND change_given = amount_tendered - amount),
    CONSTRAINT ck_pay_status CHECK (status IN ('CAPTURED', 'REVERSED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_pay_sale ON payments (sale_id);
CREATE INDEX ix_pay_tender_time ON payments (tender_type, created_at);
