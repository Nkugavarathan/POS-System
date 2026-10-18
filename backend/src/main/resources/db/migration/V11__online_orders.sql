-- V11: online shop (click & collect) with item-level packing, substitution approval, arrival
-- check-in, customer notifications (+ email/SMS outbox) and store-scoped "rescue deal" promotions.

CREATE TABLE online_orders (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    order_number        VARCHAR(30)   NOT NULL,
    store_id            BIGINT        NOT NULL,
    customer_id         BIGINT        NOT NULL,
    customer_account_id BIGINT        NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'PLACED',
    pickup_code         CHAR(6)       NOT NULL,
    pickup_after        DATETIME(6)   NULL,
    currency            CHAR(3)       NOT NULL,
    prices_include_tax  BOOLEAN       NOT NULL,
    -- as ordered
    subtotal            DECIMAL(15,2) NOT NULL,
    discount_total      DECIMAL(15,2) NOT NULL,
    tax_total           DECIMAL(15,2) NOT NULL,
    grand_total         DECIMAL(15,2) NOT NULL,
    -- after packing (unavailable items removed, substitutes priced) = what is paid at pickup
    final_total         DECIMAL(15,2) NULL,
    final_tax_total     DECIMAL(15,2) NULL,
    item_count          INT           NOT NULL,
    customer_note       VARCHAR(500)  NULL,
    stock_picked        BOOLEAN       NOT NULL DEFAULT FALSE,
    placed_at           DATETIME(6)   NOT NULL,
    confirmed_at        DATETIME(6)   NULL,
    packing_started_at  DATETIME(6)   NULL,
    ready_at            DATETIME(6)   NULL,
    collect_by          DATETIME(6)   NULL,
    arrived_at          DATETIME(6)   NULL,
    collected_at        DATETIME(6)   NULL,
    cancelled_at        DATETIME(6)   NULL,
    cancel_reason       VARCHAR(255)  NULL,
    sale_id             BIGINT        NULL,
    handled_by          BIGINT        NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_online_orders PRIMARY KEY (id),
    CONSTRAINT uk_oo_number UNIQUE (order_number),
    CONSTRAINT fk_oo_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_oo_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_oo_account FOREIGN KEY (customer_account_id) REFERENCES customer_accounts (id),
    CONSTRAINT fk_oo_sale FOREIGN KEY (sale_id) REFERENCES sales (id),
    CONSTRAINT ck_oo_status CHECK (status IN ('PLACED', 'CONFIRMED', 'PACKING', 'READY_FOR_PICKUP', 'COLLECTED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_oo_total CHECK (grand_total >= 0 AND (final_total IS NULL OR final_total >= 0))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_oo_store_status ON online_orders (store_id, status, placed_at);
CREATE INDEX ix_oo_account ON online_orders (customer_account_id, placed_at);
CREATE INDEX ix_oo_collect_by ON online_orders (status, collect_by);

CREATE TABLE online_order_items (
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    online_order_id         BIGINT        NOT NULL,
    line_number             INT           NOT NULL,
    product_id              BIGINT        NOT NULL,
    sku_snapshot            VARCHAR(40)   NOT NULL,
    barcode_snapshot        VARCHAR(32)   NULL,
    product_name_snapshot   VARCHAR(150)  NOT NULL,
    category_id             BIGINT        NOT NULL,
    unit_of_measure         VARCHAR(10)   NOT NULL,
    quantity                DECIMAL(15,3) NOT NULL,
    unit_price              DECIMAL(15,2) NOT NULL,
    gross_amount            DECIMAL(15,2) NOT NULL,
    promo_discount_amount   DECIMAL(15,2) NOT NULL,
    promotion_name          VARCHAR(100)  NULL,
    net_amount              DECIMAL(15,2) NOT NULL,
    tax_rate_percent        DECIMAL(6,3)  NOT NULL,
    tax_amount              DECIMAL(15,2) NOT NULL,
    line_total              DECIMAL(15,2) NOT NULL,
    -- customer's choice if the item is out of stock
    substitution_preference VARCHAR(10)   NOT NULL DEFAULT 'SUBSTITUTE',
    -- packing
    pick_status             VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    picked_quantity         DECIMAL(15,3) NULL,
    substitute_product_id   BIGINT        NULL,
    substitute_name         VARCHAR(150)  NULL,
    substitute_quantity     DECIMAL(15,3) NULL,
    substitute_line_total   DECIMAL(15,2) NULL,
    substitute_tax_rate     DECIMAL(6,3)  NULL,
    -- final, after packing (what the sale records)
    final_product_id        BIGINT        NULL,
    final_quantity          DECIMAL(15,3) NULL,
    final_line_total        DECIMAL(15,2) NULL,
    final_tax_amount        DECIMAL(15,2) NULL,
    unit_cost               DECIMAL(15,4) NULL,
    cost_amount             DECIMAL(15,2) NULL,
    CONSTRAINT pk_online_order_items PRIMARY KEY (id),
    CONSTRAINT uk_ooi_line UNIQUE (online_order_id, line_number),
    CONSTRAINT fk_ooi_order FOREIGN KEY (online_order_id) REFERENCES online_orders (id),
    CONSTRAINT fk_ooi_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_ooi_substitute FOREIGN KEY (substitute_product_id) REFERENCES products (id),
    CONSTRAINT ck_ooi_qty CHECK (quantity > 0),
    CONSTRAINT ck_ooi_pref CHECK (substitution_preference IN ('SUBSTITUTE', 'SKIP')),
    CONSTRAINT ck_ooi_pick CHECK (pick_status IN ('PENDING', 'PICKED', 'SUBSTITUTE_PROPOSED', 'SUBSTITUTED', 'UNAVAILABLE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE online_order_events (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    online_order_id BIGINT       NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    message         VARCHAR(255) NOT NULL,
    actor_type      VARCHAR(10)  NOT NULL,
    actor_id        BIGINT       NULL,
    created_at      DATETIME(6)  NOT NULL,
    CONSTRAINT pk_online_order_events PRIMARY KEY (id),
    CONSTRAINT fk_ooe_order FOREIGN KEY (online_order_id) REFERENCES online_orders (id),
    CONSTRAINT ck_ooe_actor CHECK (actor_type IN ('CUSTOMER', 'STAFF', 'SYSTEM'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_ooe_order ON online_order_events (online_order_id, id);

CREATE TABLE customer_notifications (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    customer_account_id BIGINT       NOT NULL,
    online_order_id     BIGINT       NULL,
    kind                VARCHAR(20)  NOT NULL DEFAULT 'ORDER',
    title               VARCHAR(120) NOT NULL,
    body                VARCHAR(500) NOT NULL,
    read_at             DATETIME(6)  NULL,
    created_at          DATETIME(6)  NOT NULL,
    CONSTRAINT pk_customer_notifications PRIMARY KEY (id),
    CONSTRAINT fk_cn_account FOREIGN KEY (customer_account_id) REFERENCES customer_accounts (id),
    CONSTRAINT fk_cn_order FOREIGN KEY (online_order_id) REFERENCES online_orders (id),
    CONSTRAINT ck_cn_kind CHECK (kind IN ('ORDER', 'SUBSTITUTION', 'ACCOUNT', 'DEAL'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_cn_account_unread ON customer_notifications (customer_account_id, read_at, id);

-- Transactional outbox: written with the status change, delivered by a scheduler, so a message is
-- never sent for a change that rolled back and never lost for one that committed.
CREATE TABLE notification_outbox (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    notification_id BIGINT       NOT NULL,
    channel         VARCHAR(10)  NOT NULL,
    recipient       VARCHAR(120) NOT NULL,
    subject         VARCHAR(120) NOT NULL,
    body            VARCHAR(500) NOT NULL,
    status          VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    attempts        INT          NOT NULL DEFAULT 0,
    last_error      VARCHAR(255) NULL,
    created_at      DATETIME(6)  NOT NULL,
    sent_at         DATETIME(6)  NULL,
    CONSTRAINT pk_notification_outbox PRIMARY KEY (id),
    CONSTRAINT fk_outbox_notification FOREIGN KEY (notification_id) REFERENCES customer_notifications (id),
    CONSTRAINT ck_outbox_channel CHECK (channel IN ('EMAIL', 'SMS')),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_outbox_pending ON notification_outbox (status, id);

ALTER TABLE sales ADD CONSTRAINT fk_sales_online_order FOREIGN KEY (online_order_id) REFERENCES online_orders (id);

-- Promotions can now be limited to one store and be generated automatically ("rescue deals" for
-- batches close to expiry).
ALTER TABLE product_promotions ADD COLUMN store_id BIGINT NULL;
ALTER TABLE product_promotions ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE product_promotions ADD COLUMN batch_id BIGINT NULL;
ALTER TABLE product_promotions ADD CONSTRAINT fk_promo_store FOREIGN KEY (store_id) REFERENCES stores (id);
ALTER TABLE product_promotions ADD CONSTRAINT fk_promo_batch FOREIGN KEY (batch_id) REFERENCES inventory_batches (id);
ALTER TABLE product_promotions ADD CONSTRAINT ck_promo_source CHECK (source IN ('MANUAL', 'RESCUE'));

-- Stock picked for an online order (and put back when the order is cancelled or not collected).
ALTER TABLE stock_movements DROP CONSTRAINT ck_sm_type;
ALTER TABLE stock_movements ADD CONSTRAINT ck_sm_type CHECK (movement_type IN ('OPENING_BALANCE', 'PURCHASE_RECEIPT',
    'SALE', 'SALE_RETURN', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'WRITE_OFF_DAMAGED', 'WRITE_OFF_EXPIRED',
    'COUNT_CORRECTION', 'TRANSFER_OUT', 'TRANSFER_IN', 'ONLINE_ORDER_PICK', 'ONLINE_ORDER_RELEASE'));

INSERT INTO permissions (code, module, description) VALUES
 ('ONLINE_ORDER_MANAGE', 'shop', 'Confirm, pack, substitute, mark ready and cancel online orders'),
 ('RESCUE_DEAL_MANAGE',  'shop', 'Run and configure near-expiry rescue deals');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'ONLINE_ORDER_MANAGE'
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'INVENTORY_STAFF', 'CASHIER');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'RESCUE_DEAL_MANAGE'
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN', 'MANAGER');

INSERT INTO settings (store_id, setting_key, setting_value, value_type, updated_at) VALUES
 (NULL, 'shop.enabled',             'true', 'BOOLEAN', UTC_TIMESTAMP(6)),
 (NULL, 'shop.pickupHoldDays',      '3',    'INTEGER', UTC_TIMESTAMP(6)),
 (NULL, 'shop.maxLinesPerOrder',    '60',   'INTEGER', UTC_TIMESTAMP(6)),
 (NULL, 'rescue.enabled',           'true', 'BOOLEAN', UTC_TIMESTAMP(6)),
 (NULL, 'rescue.daysBeforeExpiry',  '3',    'INTEGER', UTC_TIMESTAMP(6)),
 (NULL, 'rescue.discountPercent',   '30',   'DECIMAL', UTC_TIMESTAMP(6)),
 (NULL, 'rescue.lastDayPercent',    '50',   'DECIMAL', UTC_TIMESTAMP(6));
