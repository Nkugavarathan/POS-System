-- V11: online shop (click & collect): orders, status timeline, customer notifications and an
-- outbox for email/SMS delivery.

CREATE TABLE online_orders (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    order_number        VARCHAR(30)   NOT NULL,
    store_id            BIGINT        NOT NULL,
    customer_id         BIGINT        NOT NULL,
    customer_account_id BIGINT        NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'PLACED',
    fulfillment_type    VARCHAR(20)   NOT NULL DEFAULT 'PICKUP',
    pickup_code         CHAR(6)       NOT NULL,
    currency            CHAR(3)       NOT NULL,
    prices_include_tax  BOOLEAN       NOT NULL,
    subtotal            DECIMAL(15,2) NOT NULL,
    discount_total      DECIMAL(15,2) NOT NULL,
    tax_total           DECIMAL(15,2) NOT NULL,
    grand_total         DECIMAL(15,2) NOT NULL,
    item_count          INT           NOT NULL,
    customer_note       VARCHAR(500)  NULL,
    stock_picked        BOOLEAN       NOT NULL DEFAULT FALSE,
    placed_at           DATETIME(6)   NOT NULL,
    confirmed_at        DATETIME(6)   NULL,
    packing_started_at  DATETIME(6)   NULL,
    ready_at            DATETIME(6)   NULL,
    collect_by          DATETIME(6)   NULL,
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
    CONSTRAINT ck_oo_total CHECK (grand_total >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_oo_store_status ON online_orders (store_id, status, placed_at);
CREATE INDEX ix_oo_account ON online_orders (customer_account_id, placed_at);
CREATE INDEX ix_oo_collect_by ON online_orders (status, collect_by);

CREATE TABLE online_order_items (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    online_order_id       BIGINT        NOT NULL,
    line_number           INT           NOT NULL,
    product_id            BIGINT        NOT NULL,
    sku_snapshot          VARCHAR(40)   NOT NULL,
    barcode_snapshot      VARCHAR(32)   NULL,
    product_name_snapshot VARCHAR(150)  NOT NULL,
    category_id           BIGINT        NOT NULL,
    unit_of_measure       VARCHAR(10)   NOT NULL,
    quantity              DECIMAL(15,3) NOT NULL,
    unit_price            DECIMAL(15,2) NOT NULL,
    gross_amount          DECIMAL(15,2) NOT NULL,
    promo_discount_amount DECIMAL(15,2) NOT NULL,
    promotion_name        VARCHAR(100)  NULL,
    net_amount            DECIMAL(15,2) NOT NULL,
    tax_rate_percent      DECIMAL(6,3)  NOT NULL,
    tax_amount            DECIMAL(15,2) NOT NULL,
    line_total            DECIMAL(15,2) NOT NULL,
    unit_cost             DECIMAL(15,4) NULL,
    cost_amount           DECIMAL(15,2) NULL,
    CONSTRAINT pk_online_order_items PRIMARY KEY (id),
    CONSTRAINT uk_ooi_line UNIQUE (online_order_id, line_number),
    CONSTRAINT fk_ooi_order FOREIGN KEY (online_order_id) REFERENCES online_orders (id),
    CONSTRAINT fk_ooi_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_ooi_qty CHECK (quantity > 0)
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
    title               VARCHAR(120) NOT NULL,
    body                VARCHAR(500) NOT NULL,
    read_at             DATETIME(6)  NULL,
    created_at          DATETIME(6)  NOT NULL,
    CONSTRAINT pk_customer_notifications PRIMARY KEY (id),
    CONSTRAINT fk_cn_account FOREIGN KEY (customer_account_id) REFERENCES customer_accounts (id),
    CONSTRAINT fk_cn_order FOREIGN KEY (online_order_id) REFERENCES online_orders (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_cn_account_unread ON customer_notifications (customer_account_id, read_at, id);

-- Transactional outbox: rows are written with the status change and delivered by a scheduler,
-- so a notification is never lost (or sent) for a change that rolled back.
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

-- Stock picked for an online order (and returned when the order is cancelled or expires).
ALTER TABLE stock_movements DROP CONSTRAINT ck_sm_type;
ALTER TABLE stock_movements ADD CONSTRAINT ck_sm_type CHECK (movement_type IN ('OPENING_BALANCE', 'PURCHASE_RECEIPT',
    'SALE', 'SALE_RETURN', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'WRITE_OFF_DAMAGED', 'WRITE_OFF_EXPIRED',
    'COUNT_CORRECTION', 'TRANSFER_OUT', 'TRANSFER_IN', 'ONLINE_ORDER_PICK', 'ONLINE_ORDER_RELEASE'));

INSERT INTO permissions (code, module, description) VALUES
 ('ONLINE_ORDER_MANAGE', 'shop', 'Confirm, pack, mark ready and cancel online orders');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'ONLINE_ORDER_MANAGE'
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'INVENTORY_STAFF', 'CASHIER');

INSERT INTO settings (store_id, setting_key, setting_value, value_type, updated_at) VALUES
 (NULL, 'shop.enabled',          'true', 'BOOLEAN', UTC_TIMESTAMP(6)),
 (NULL, 'shop.pickupHoldDays',   '3',    'INTEGER', UTC_TIMESTAMP(6)),
 (NULL, 'shop.maxItemsPerOrder', '100',  'INTEGER', UTC_TIMESTAMP(6));
