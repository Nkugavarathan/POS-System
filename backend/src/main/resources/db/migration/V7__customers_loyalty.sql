-- V7: customers, loyalty ledger and online-shop customer accounts.
-- Only the personal data the business needs is stored (name, phone, optional email).

CREATE TABLE customers (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    customer_code     VARCHAR(20)  NOT NULL,
    full_name         VARCHAR(120) NOT NULL,
    phone             VARCHAR(20)  NULL,
    email             VARCHAR(120) NULL,
    marketing_consent BOOLEAN      NOT NULL DEFAULT FALSE,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_customers PRIMARY KEY (id),
    CONSTRAINT uk_cust_code UNIQUE (customer_code),
    CONSTRAINT uk_cust_phone UNIQUE (phone),
    CONSTRAINT uk_cust_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_cust_name ON customers (full_name);

CREATE TABLE loyalty_accounts (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    customer_id    BIGINT      NOT NULL,
    card_number    VARCHAR(20) NOT NULL,
    points_balance BIGINT      NOT NULL DEFAULT 0,
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at      DATETIME(6) NOT NULL,
    version        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_loyalty_accounts PRIMARY KEY (id),
    CONSTRAINT uk_la_customer UNIQUE (customer_id),
    CONSTRAINT uk_la_card UNIQUE (card_number),
    CONSTRAINT fk_la_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT ck_la_nonneg CHECK (points_balance >= 0),
    CONSTRAINT ck_la_status CHECK (status IN ('ACTIVE', 'BLOCKED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE loyalty_transactions (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    loyalty_account_id BIGINT       NOT NULL,
    transaction_type   VARCHAR(20)  NOT NULL,
    points             BIGINT       NOT NULL,
    balance_after      BIGINT       NOT NULL,
    reference_type     VARCHAR(30)  NULL,
    reference_id       BIGINT       NULL,
    reason             VARCHAR(255) NULL,
    created_by         BIGINT       NULL,
    created_at         DATETIME(6)  NOT NULL,
    CONSTRAINT pk_loyalty_transactions PRIMARY KEY (id),
    CONSTRAINT fk_lt_account FOREIGN KEY (loyalty_account_id) REFERENCES loyalty_accounts (id),
    CONSTRAINT ck_lt_type CHECK (transaction_type IN ('EARN', 'REDEEM', 'EARN_REVERSAL', 'REDEEM_REVERSAL', 'ADJUSTMENT')),
    CONSTRAINT ck_lt_nonzero CHECK (points <> 0),
    CONSTRAINT ck_lt_balance CHECK (balance_after >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_lt_account_time ON loyalty_transactions (loyalty_account_id, created_at);

-- Online shop sign-in for customers. Separate from staff users: a customer token can never
-- reach a back-office endpoint (no staff permissions are ever issued to it).
CREATE TABLE customer_accounts (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    customer_id         BIGINT       NOT NULL,
    email               VARCHAR(120) NOT NULL,
    password_hash       VARCHAR(100) NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    failed_login_count  INT          NOT NULL DEFAULT 0,
    locked_until        DATETIME(6)  NULL,
    last_login_at       DATETIME(6)  NULL,
    preferred_store_id  BIGINT       NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_customer_accounts PRIMARY KEY (id),
    CONSTRAINT uk_ca_email UNIQUE (email),
    CONSTRAINT uk_ca_customer UNIQUE (customer_id),
    CONSTRAINT fk_ca_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_ca_store FOREIGN KEY (preferred_store_id) REFERENCES stores (id),
    CONSTRAINT ck_ca_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE customer_refresh_tokens (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    account_id        BIGINT       NOT NULL,
    token_hash        CHAR(64)     NOT NULL,
    family_id         CHAR(36)     NOT NULL,
    issued_at         DATETIME(6)  NOT NULL,
    expires_at        DATETIME(6)  NOT NULL,
    family_expires_at DATETIME(6)  NOT NULL,
    revoked_at        DATETIME(6)  NULL,
    revoke_reason     VARCHAR(30)  NULL,
    replaced_by_id    BIGINT       NULL,
    CONSTRAINT pk_customer_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_crt_hash UNIQUE (token_hash),
    CONSTRAINT fk_crt_account FOREIGN KEY (account_id) REFERENCES customer_accounts (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_crt_family ON customer_refresh_tokens (family_id);
