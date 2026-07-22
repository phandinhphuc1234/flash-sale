--liquibase formatted sql

--changeset philia:001-create-product-catalog-schema dbms:postgresql runInTransaction:true
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:product_db SELECT current_database()

CREATE TABLE categories (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    parent_id UUID,
    code VARCHAR(64) NOT NULL,
    slug VARCHAR(200) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uq_categories_code UNIQUE (code),
    CONSTRAINT uq_categories_slug UNIQUE (slug),
    CONSTRAINT fk_categories_parent
        FOREIGN KEY (parent_id) REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT ck_categories_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_categories_slug_not_blank CHECK (btrim(slug) <> ''),
    CONSTRAINT ck_categories_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_categories_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_categories_sort_order_non_negative CHECK (sort_order >= 0),
    CONSTRAINT ck_categories_version_non_negative CHECK (version >= 0)
);

CREATE TABLE products (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    code VARCHAR(64) NOT NULL,
    slug VARCHAR(200) NOT NULL,
    name VARCHAR(255) NOT NULL,
    short_description VARCHAR(500),
    description TEXT,
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT uq_products_code UNIQUE (code),
    CONSTRAINT uq_products_slug UNIQUE (slug),
    CONSTRAINT ck_products_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_products_slug_not_blank CHECK (btrim(slug) <> ''),
    CONSTRAINT ck_products_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_products_attributes_object
        CHECK (jsonb_typeof(attributes) = 'object'),
    CONSTRAINT ck_products_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_products_version_non_negative CHECK (version >= 0)
);

CREATE TABLE product_variants (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL,
    sku VARCHAR(100) NOT NULL,
    barcode VARCHAR(64),
    name VARCHAR(255) NOT NULL,
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    base_price NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    weight_grams INTEGER,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_product_variants PRIMARY KEY (id),
    CONSTRAINT uq_product_variants_sku UNIQUE (sku),
    CONSTRAINT uq_product_variants_barcode UNIQUE (barcode),
    CONSTRAINT uq_product_variants_product_id_id UNIQUE (product_id, id),
    CONSTRAINT fk_product_variants_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT,
    CONSTRAINT ck_product_variants_sku_not_blank CHECK (btrim(sku) <> ''),
    CONSTRAINT ck_product_variants_barcode_not_blank
        CHECK (barcode IS NULL OR btrim(barcode) <> ''),
    CONSTRAINT ck_product_variants_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_product_variants_attributes_object
        CHECK (jsonb_typeof(attributes) = 'object'),
    CONSTRAINT ck_product_variants_base_price_non_negative CHECK (base_price >= 0),
    CONSTRAINT ck_product_variants_currency_vnd CHECK (currency = 'VND'),
    CONSTRAINT ck_product_variants_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_product_variants_weight_positive
        CHECK (weight_grams IS NULL OR weight_grams > 0),
    CONSTRAINT ck_product_variants_sort_order_non_negative CHECK (sort_order >= 0),
    CONSTRAINT ck_product_variants_version_non_negative CHECK (version >= 0)
);

CREATE TABLE product_categories (
    product_id UUID NOT NULL,
    category_id UUID NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_product_categories PRIMARY KEY (product_id, category_id),
    CONSTRAINT fk_product_categories_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT,
    CONSTRAINT fk_product_categories_category
        FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT ck_product_categories_sort_order_non_negative CHECK (sort_order >= 0)
);

CREATE TABLE product_media (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL,
    variant_id UUID,
    media_type VARCHAR(20) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    alt_text VARCHAR(500),
    mime_type VARCHAR(100),
    width_px INTEGER,
    height_px INTEGER,
    file_size_bytes BIGINT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_product_media PRIMARY KEY (id),
    CONSTRAINT fk_product_media_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT,
    CONSTRAINT fk_product_media_product_variant
        FOREIGN KEY (product_id, variant_id)
        REFERENCES product_variants (product_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_product_media_type CHECK (media_type IN ('IMAGE', 'VIDEO')),
    CONSTRAINT ck_product_media_url_not_blank CHECK (btrim(url) <> ''),
    CONSTRAINT ck_product_media_width_positive
        CHECK (width_px IS NULL OR width_px > 0),
    CONSTRAINT ck_product_media_height_positive
        CHECK (height_px IS NULL OR height_px > 0),
    CONSTRAINT ck_product_media_file_size_positive
        CHECK (file_size_bytes IS NULL OR file_size_bytes > 0),
    CONSTRAINT ck_product_media_sort_order_non_negative CHECK (sort_order >= 0),
    CONSTRAINT ck_product_media_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_categories_parent_sort
    ON categories (parent_id, sort_order, id);

CREATE INDEX idx_products_status_published
    ON products (status, published_at DESC, id);

CREATE INDEX idx_product_variants_product_sort
    ON product_variants (product_id, sort_order, id);

CREATE INDEX idx_product_categories_category_sort_product
    ON product_categories (category_id, sort_order, product_id);

CREATE UNIQUE INDEX uq_product_categories_one_primary
    ON product_categories (product_id)
    WHERE is_primary = TRUE;

CREATE INDEX idx_product_media_product_variant_sort
    ON product_media (product_id, variant_id, sort_order, id);

--rollback DROP TABLE product_media;
--rollback DROP TABLE product_categories;
--rollback DROP TABLE product_variants;
--rollback DROP TABLE products;
--rollback DROP TABLE categories;
