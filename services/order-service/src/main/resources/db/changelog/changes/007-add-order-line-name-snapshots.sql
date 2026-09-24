--liquibase formatted sql

-- Feature 054 captures immutable Product-owned display names on newly created Order lines.
-- Existing rows intentionally remain null because current catalog names are not historical truth.
--changeset order:007-add-order-line-name-snapshots

ALTER TABLE order_lines
    ADD COLUMN product_name_snapshot VARCHAR(255),
    ADD COLUMN variant_name_snapshot VARCHAR(255);
