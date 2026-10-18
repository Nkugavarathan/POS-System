-- V6: cashier shifts, cash drawer movements, held carts and pre-payment voids.

CREATE TABLE cashier_shifts (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    shift_number         VARCHAR(50)   NOT NULL,
    store_id             BIGINT        NOT NULL,
    register_id          BIGINT        NOT NULL,
    cashier_id           BIGINT        NOT NULL,
    status               VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    opened_at            DATETIME(6)   NOT NULL,
    opening_float        DECIMAL(15,2) NOT NULL,
    closed_at            DATETIME(6)   NULL,
    closed_by            BIGINT        NULL,
    expected_cash        DECIMAL(15,2) NULL,
    counted_cash         DECIMAL(15,2) NULL,
    variance             DECIMAL(15,2) NULL,
    variance_approved_by BIGINT        NULL,
    close_notes          VARCHAR(500)  NULL,
    denomination_json    VARCHAR(1000) NULL,
    version              BIGINT        NOT NULL DEFAULT 0,
    -- Only one OPEN shift per register and per cashier: NULL for closed rows, and unique
    -- indexes ignore NULLs (works on MySQL 8 and MariaDB).
    open_register_key    BIGINT AS (IF(status = 'OPEN', register_id, NULL)) STORED,
    open_cashier_key     BIGINT AS (IF(status = 'OPEN', cashier_id, NULL)) STORED,
    CONSTRAINT pk_cashier_shifts PRIMARY KEY (id),
    CONSTRAINT uk_shift_number UNIQUE (shift_number),
    CONSTRAINT uk_shift_open_register UNIQUE (open_register_key),
    CONSTRAINT uk_shift_open_cashier UNIQUE (open_cashier_key),
    CONSTRAINT fk_shift_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_shift_register FOREIGN KEY (register_id) REFERENCES registers (id),
    CONSTRAINT fk_shift_cashier FOREIGN KEY (cashier_id) REFERENCES users (id),
    CONSTRAINT ck_shift_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_shift_float CHECK (opening_float >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_shift_store_opened ON cashier_shifts (store_id, opened_at);

CREATE TABLE cash_movements (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    shift_id      BIGINT        NOT NULL,
    movement_type VARCHAR(20)   NOT NULL,
    amount        DECIMAL(15,2) NOT NULL,
    reason        VARCHAR(255)  NOT NULL,
    performed_by  BIGINT        NOT NULL,
    approved_by   BIGINT        NULL,
    created_at    DATETIME(6)   NOT NULL,
    CONSTRAINT pk_cash_movements PRIMARY KEY (id),
    CONSTRAINT fk_cm_shift FOREIGN KEY (shift_id) REFERENCES cashier_shifts (id),
    CONSTRAINT fk_cm_user FOREIGN KEY (performed_by) REFERENCES users (id),
    CONSTRAINT ck_cm_type CHECK (movement_type IN ('PAY_IN', 'PAY_OUT', 'CASH_DROP')),
    CONSTRAINT ck_cm_amount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_cm_shift ON cash_movements (shift_id);

CREATE TABLE held_carts (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    store_id    BIGINT       NOT NULL,
    register_id BIGINT       NOT NULL,
    shift_id    BIGINT       NOT NULL,
    held_by     BIGINT       NOT NULL,
    customer_id BIGINT       NULL,
    label       VARCHAR(60)  NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'HELD',
    held_at     DATETIME(6)  NOT NULL,
    resumed_at  DATETIME(6)  NULL,
    resumed_by  BIGINT       NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_held_carts PRIMARY KEY (id),
    CONSTRAINT fk_held_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_held_shift FOREIGN KEY (shift_id) REFERENCES cashier_shifts (id),
    CONSTRAINT ck_held_status CHECK (status IN ('HELD', 'RESUMED', 'CANCELLED', 'EXPIRED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_held_store_status ON held_carts (store_id, status);

CREATE TABLE held_cart_items (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    held_cart_id BIGINT        NOT NULL,
    product_id   BIGINT        NOT NULL,
    quantity     DECIMAL(15,3) NOT NULL,
    line_number  INT           NOT NULL,
    CONSTRAINT pk_held_cart_items PRIMARY KEY (id),
    CONSTRAINT fk_hci_cart FOREIGN KEY (held_cart_id) REFERENCES held_carts (id) ON DELETE CASCADE,
    CONSTRAINT fk_hci_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_hci_qty CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE transaction_voids (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    store_id         BIGINT        NOT NULL,
    register_id      BIGINT        NOT NULL,
    shift_id         BIGINT        NOT NULL,
    cashier_id       BIGINT        NOT NULL,
    approved_by      BIGINT        NULL,
    void_type        VARCHAR(10)   NOT NULL,
    reason_code      VARCHAR(30)   NOT NULL,
    reason_text      VARCHAR(255)  NULL,
    items_json       LONGTEXT      NOT NULL,
    estimated_amount DECIMAL(15,2) NOT NULL,
    created_at       DATETIME(6)   NOT NULL,
    CONSTRAINT pk_transaction_voids PRIMARY KEY (id),
    CONSTRAINT fk_void_shift FOREIGN KEY (shift_id) REFERENCES cashier_shifts (id),
    CONSTRAINT ck_void_type CHECK (void_type IN ('LINE', 'CART'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_voids_store_time ON transaction_voids (store_id, created_at);
