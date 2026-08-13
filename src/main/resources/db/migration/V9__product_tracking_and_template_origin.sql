ALTER TABLE products
    ADD COLUMN source_template_id UUID NULL REFERENCES product_templates(id),
    ADD COLUMN inventory_tracking_mode VARCHAR(16) NOT NULL DEFAULT 'QUANTITY',
    ADD CONSTRAINT products_inventory_tracking_mode_check
        CHECK (inventory_tracking_mode IN ('QUANTITY', 'PRESENCE'));

CREATE INDEX products_user_source_template_idx ON products(user_id, source_template_id);
CREATE UNIQUE INDEX products_user_source_template_unique
    ON products(user_id, source_template_id) WHERE source_template_id IS NOT NULL;

ALTER TABLE inventory_items DROP CONSTRAINT inventory_items_quantity_check;
ALTER TABLE inventory_items ALTER COLUMN quantity DROP NOT NULL;
ALTER TABLE inventory_items ADD CONSTRAINT inventory_items_quantity_check
    CHECK (quantity IS NULL OR quantity >= 0);

ALTER TABLE shopping_list_items DROP CONSTRAINT shopping_list_items_quantity_check;
ALTER TABLE shopping_list_items ALTER COLUMN quantity DROP NOT NULL;
ALTER TABLE shopping_list_items ADD COLUMN inventory_was_present BOOLEAN NULL;
ALTER TABLE shopping_list_items ADD CONSTRAINT shopping_list_items_quantity_check
    CHECK (quantity IS NULL OR (quantity > 0 AND MOD(quantity * 2, 1) = 0));
