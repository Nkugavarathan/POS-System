-- V4: catalog - categories, tax rates, products, barcodes, promotions.

CREATE TABLE categories (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id   BIGINT       NULL,
    code        VARCHAR(30)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uk_categories_code UNIQUE (code),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_categories_parent ON categories (parent_id);

-- Tax rates are configuration. The seeded values are EXAMPLES; confirm the applicable rates
-- with a tax adviser before going live.
CREATE TABLE tax_rates (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    code           VARCHAR(20)  NOT NULL,
    name           VARCHAR(80)  NOT NULL,
    rate_percent   DECIMAL(6,3) NOT NULL,
    effective_from DATE         NOT NULL,
    effective_to   DATE         NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_tax_rates PRIMARY KEY (id),
    CONSTRAINT uk_tax_rates_code UNIQUE (code),
    CONSTRAINT ck_tax_rate CHECK (rate_percent >= 0 AND rate_percent <= 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE products (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    sku                  VARCHAR(40)   NOT NULL,
    name                 VARCHAR(150)  NOT NULL,
    description          VARCHAR(1000) NULL,
    category_id          BIGINT        NOT NULL,
    brand                VARCHAR(80)   NULL,
    unit_of_measure      VARCHAR(10)   NOT NULL,
    cost_price           DECIMAL(15,4) NOT NULL DEFAULT 0,
    selling_price        DECIMAL(15,2) NOT NULL,
    tax_rate_id          BIGINT        NOT NULL,
    reorder_level        DECIMAL(15,3) NOT NULL DEFAULT 0,
    track_batches        BOOLEAN       NOT NULL DEFAULT FALSE,
    track_expiry         BOOLEAN       NOT NULL DEFAULT FALSE,
    allow_price_override BOOLEAN       NOT NULL DEFAULT FALSE,
    image_url            VARCHAR(500)  NULL,
    active               BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by           BIGINT        NULL,
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT uk_products_sku UNIQUE (sku),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_products_tax_rate FOREIGN KEY (tax_rate_id) REFERENCES tax_rates (id),
    CONSTRAINT ck_products_uom CHECK (unit_of_measure IN ('EACH', 'PACK', 'BOTTLE', 'KG', 'G', 'L', 'ML')),
    CONSTRAINT ck_products_prices CHECK (cost_price >= 0 AND selling_price >= 0),
    CONSTRAINT ck_products_reorder CHECK (reorder_level >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_products_name ON products (name);
CREATE INDEX ix_products_category_active ON products (category_id, active);
CREATE INDEX ix_products_brand ON products (brand);

-- Barcodes are stored normalized (GTIN-13 for EAN/UPC), so UPC-A and EAN-13 scans of the same
-- item resolve to one row. A product may carry several barcodes (pack variants, old labels).
CREATE TABLE product_barcodes (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    product_id   BIGINT      NOT NULL,
    barcode      VARCHAR(32) NOT NULL,
    barcode_type VARCHAR(20) NOT NULL,
    is_primary   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   DATETIME(6) NOT NULL,
    CONSTRAINT pk_product_barcodes PRIMARY KEY (id),
    CONSTRAINT uk_pb_barcode UNIQUE (barcode),
    CONSTRAINT fk_pb_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_pb_type CHECK (barcode_type IN ('EAN_13', 'EAN_8', 'UPC_A', 'UPC_E', 'GTIN_14', 'CODE_128', 'INTERNAL'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_pb_product ON product_barcodes (product_id);

CREATE TABLE product_promotions (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    product_id     BIGINT        NOT NULL,
    name           VARCHAR(100)  NOT NULL,
    discount_type  VARCHAR(10)   NOT NULL,
    discount_value DECIMAL(15,2) NOT NULL,
    starts_at      DATETIME(6)   NOT NULL,
    ends_at        DATETIME(6)   NOT NULL,
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by     BIGINT        NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_product_promotions PRIMARY KEY (id),
    CONSTRAINT fk_promo_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_promo_type CHECK (discount_type IN ('PERCENT', 'AMOUNT')),
    CONSTRAINT ck_promo_value CHECK (discount_value > 0 AND (discount_type <> 'PERCENT' OR discount_value <= 100)),
    CONSTRAINT ck_promo_window CHECK (ends_at > starts_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_promo_product_window ON product_promotions (product_id, active, starts_at, ends_at);

INSERT INTO tax_rates (code, name, rate_percent, effective_from, active) VALUES
 ('VAT_STD',    'VAT standard rate (example - verify)', 18.000, '2024-01-01', TRUE),
 ('VAT_EXEMPT', 'VAT exempt',                             0.000, '2024-01-01', TRUE),
 ('ZERO',       'Zero-rated',                             0.000, '2024-01-01', TRUE);
