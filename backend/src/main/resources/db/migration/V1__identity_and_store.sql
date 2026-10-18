-- V1: stores, registers, identity & access, settings, tender types and the
-- cross-cutting infrastructure tables (document sequences, idempotency, audit).
-- Portable subset of MySQL 8 / MariaDB 10.4+ SQL. All timestamps are UTC.

CREATE TABLE stores (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    code                VARCHAR(20)  NOT NULL,
    name                VARCHAR(120) NOT NULL,
    address_line1       VARCHAR(150) NULL,
    address_line2       VARCHAR(150) NULL,
    city                VARCHAR(80)  NULL,
    phone               VARCHAR(30)  NULL,
    email               VARCHAR(120) NULL,
    tax_registration_no VARCHAR(40)  NULL,
    currency            CHAR(3)      NOT NULL DEFAULT 'LKR',
    timezone            VARCHAR(40)  NOT NULL DEFAULT 'Asia/Colombo',
    prices_include_tax  BOOLEAN      NOT NULL DEFAULT TRUE,
    receipt_header      VARCHAR(500) NULL,
    receipt_footer      VARCHAR(500) NULL,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_stores PRIMARY KEY (id),
    CONSTRAINT uk_stores_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE registers (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    store_id   BIGINT      NOT NULL,
    code       VARCHAR(20) NOT NULL,
    name       VARCHAR(80) NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_registers PRIMARY KEY (id),
    CONSTRAINT uk_registers_store_code UNIQUE (store_id, code),
    CONSTRAINT fk_registers_store FOREIGN KEY (store_id) REFERENCES stores (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE users (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    username             VARCHAR(50)  NOT NULL,
    full_name            VARCHAR(120) NOT NULL,
    email                VARCHAR(120) NULL,
    password_hash        VARCHAR(100) NOT NULL,
    pin_hash             VARCHAR(100) NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    failed_login_count   INT          NOT NULL DEFAULT 0,
    locked_until         DATETIME(6)  NULL,
    last_login_at        DATETIME(6)  NULL,
    password_changed_at  DATETIME(6)  NULL,
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
    CONSTRAINT ck_users_failed CHECK (failed_login_count >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE roles (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    code                 VARCHAR(40)  NOT NULL,
    name                 VARCHAR(80)  NOT NULL,
    description          VARCHAR(255) NULL,
    max_discount_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    system_role          BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uk_roles_code UNIQUE (code),
    CONSTRAINT ck_roles_discount CHECK (max_discount_percent >= 0 AND max_discount_percent <= 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE permissions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(60)  NOT NULL,
    description VARCHAR(255) NULL,
    module      VARCHAR(40)  NOT NULL,
    CONSTRAINT pk_permissions PRIMARY KEY (id),
    CONSTRAINT uk_permissions_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_perm_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_role_perm_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_store_access (
    user_id    BIGINT  NOT NULL,
    store_id   BIGINT  NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_user_store_access PRIMARY KEY (user_id, store_id),
    CONSTRAINT fk_usa_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_usa_store FOREIGN KEY (store_id) REFERENCES stores (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Typed key/value settings. store_id NULL = global default; a store row overrides it.
CREATE TABLE settings (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    store_id        BIGINT       NULL,
    scope_store_key BIGINT AS (COALESCE(store_id, 0)) STORED,
    setting_key     VARCHAR(80)  NOT NULL,
    setting_value   VARCHAR(500) NOT NULL,
    value_type      VARCHAR(20)  NOT NULL,
    updated_by      BIGINT       NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT pk_settings PRIMARY KEY (id),
    CONSTRAINT uk_settings_scope_key UNIQUE (scope_store_key, setting_key),
    CONSTRAINT fk_settings_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT ck_settings_type CHECK (value_type IN ('STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DURATION'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE tender_types (
    code               VARCHAR(20) NOT NULL,
    name               VARCHAR(60) NOT NULL,
    is_cash            BOOLEAN     NOT NULL DEFAULT FALSE,
    requires_reference BOOLEAN     NOT NULL DEFAULT FALSE,
    allows_change      BOOLEAN     NOT NULL DEFAULT FALSE,
    active             BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order         INT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_tender_types PRIMARY KEY (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Gap-free per-scope counters for human-readable document numbers (receipts, GRNs, ...).
CREATE TABLE document_sequences (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    sequence_type VARCHAR(30) NOT NULL,
    scope_code    VARCHAR(40) NOT NULL,
    period_key    VARCHAR(10) NOT NULL,
    next_value    BIGINT      NOT NULL,
    CONSTRAINT pk_document_sequences PRIMARY KEY (id),
    CONSTRAINT uk_docseq UNIQUE (sequence_type, scope_code, period_key),
    CONSTRAINT ck_docseq_next CHECK (next_value > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Written inside the business transaction: a concurrent duplicate blocks on the
-- unique index and then either replays the committed result or proceeds after a rollback.
CREATE TABLE idempotency_records (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         BIGINT      NOT NULL,
    operation       VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash    CHAR(64)    NOT NULL,
    resource_type   VARCHAR(40) NULL,
    resource_id     BIGINT      NULL,
    response_status INT         NULL,
    created_at      DATETIME(6) NOT NULL,
    expires_at      DATETIME(6) NOT NULL,
    CONSTRAINT pk_idempotency_records PRIMARY KEY (id),
    CONSTRAINT uk_idem UNIQUE (user_id, operation, idempotency_key),
    CONSTRAINT fk_idem_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_idem_expires ON idempotency_records (expires_at);

-- Append-only audit trail. Deliberately no FK on actor/store so audit rows survive any
-- future data archiving and can record failed attempts by unknown usernames.
CREATE TABLE audit_logs (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    occurred_at    DATETIME(6)  NOT NULL,
    actor_user_id  BIGINT       NULL,
    actor_username VARCHAR(50)  NULL,
    store_id       BIGINT       NULL,
    action         VARCHAR(60)  NOT NULL,
    entity_type    VARCHAR(60)  NULL,
    entity_id      VARCHAR(60)  NULL,
    outcome        VARCHAR(20)  NOT NULL,
    summary        VARCHAR(500) NULL,
    details        LONGTEXT     NULL,
    ip_address     VARCHAR(45)  NULL,
    user_agent     VARCHAR(255) NULL,
    correlation_id VARCHAR(64)  NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT ck_audit_outcome CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILURE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_audit_time ON audit_logs (occurred_at);
CREATE INDEX ix_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX ix_audit_actor_time ON audit_logs (actor_user_id, occurred_at);
CREATE INDEX ix_audit_action_time ON audit_logs (action, occurred_at);
