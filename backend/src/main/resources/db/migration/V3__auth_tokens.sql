-- V3: refresh-token families (rotation + reuse detection) and single-use manager approvals.

CREATE TABLE refresh_tokens (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL,
    token_hash        CHAR(64)     NOT NULL,
    family_id         CHAR(36)     NOT NULL,
    issued_at         DATETIME(6)  NOT NULL,
    expires_at        DATETIME(6)  NOT NULL,
    family_expires_at DATETIME(6)  NOT NULL,
    revoked_at        DATETIME(6)  NULL,
    revoke_reason     VARCHAR(30)  NULL,
    replaced_by_id    BIGINT       NULL,
    user_agent        VARCHAR(255) NULL,
    ip_address        VARCHAR(45)  NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_rt_hash UNIQUE (token_hash),
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_rt_replaced_by FOREIGN KEY (replaced_by_id) REFERENCES refresh_tokens (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_rt_family ON refresh_tokens (family_id);
CREATE INDEX ix_rt_user ON refresh_tokens (user_id);
CREATE INDEX ix_rt_expires ON refresh_tokens (family_expires_at);

CREATE TABLE approval_grants (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    token_hash        CHAR(64)      NOT NULL,
    action            VARCHAR(40)   NOT NULL,
    store_id          BIGINT        NOT NULL,
    requested_by      BIGINT        NOT NULL,
    approved_by       BIGINT        NOT NULL,
    register_id       BIGINT        NULL,
    limit_value       DECIMAL(15,2) NULL,
    context_json      VARCHAR(1000) NULL,
    issued_at         DATETIME(6)   NOT NULL,
    expires_at        DATETIME(6)   NOT NULL,
    consumed_at       DATETIME(6)   NULL,
    consumed_ref_type VARCHAR(40)   NULL,
    consumed_ref_id   BIGINT        NULL,
    CONSTRAINT pk_approval_grants PRIMARY KEY (id),
    CONSTRAINT uk_ag_hash UNIQUE (token_hash),
    CONSTRAINT fk_ag_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_ag_requested_by FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT fk_ag_approved_by FOREIGN KEY (approved_by) REFERENCES users (id),
    CONSTRAINT fk_ag_register FOREIGN KEY (register_id) REFERENCES registers (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_ag_expires ON approval_grants (expires_at);
