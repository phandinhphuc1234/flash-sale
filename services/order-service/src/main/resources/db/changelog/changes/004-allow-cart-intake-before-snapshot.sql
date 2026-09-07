--liquibase formatted sql

-- A Cart browser request does not carry cart_id. Order first persists the idempotency intake, then
-- attaches the Cart-owned identity only after the internal snapshot succeeds. Keep the earlier
-- expand-only changeset immutable and adjust this state-aware rule in a forward-only changeset.
--changeset order:004-allow-cart-intake-before-snapshot

ALTER TABLE regular_purchase_requests
    DROP CONSTRAINT ck_regular_purchase_requests_cart_source,
    ADD CONSTRAINT ck_regular_purchase_requests_cart_source CHECK (
        (source = 'BUY_NOW' AND cart_id IS NULL AND cart_version IS NULL)
        OR (
            source = 'CART'
            AND (
                (state IN ('RECEIVED', 'REJECTED') AND cart_id IS NULL AND cart_version IS NULL)
                OR (
                    state IN ('SNAPSHOT_VALIDATED', 'PRODUCT_VALIDATED', 'HOLD_ACQUIRED', 'ACCEPTED', 'REJECTED')
                    AND cart_id IS NOT NULL
                    AND cart_version IS NOT NULL
                    AND cart_version >= 0
                )
            )
        )
    );
