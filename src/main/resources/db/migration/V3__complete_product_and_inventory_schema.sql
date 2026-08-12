ALTER TABLE products
    ADD COLUMN name VARCHAR(255) NOT NULL,
    ADD COLUMN category VARCHAR(32) NOT NULL,
    ADD COLUMN default_unit VARCHAR(32) NOT NULL,
    ADD CONSTRAINT products_category_check
        CHECK (category IN ('MEAT', 'VEGETABLE', 'FRUIT', 'DAIRY', 'DRY_GOODS', 'SPICE', 'OTHER')),
    ADD CONSTRAINT products_default_unit_check
        CHECK (default_unit IN ('GRAM', 'MILLILITER', 'PIECE'));

ALTER TABLE inventory_items
    ADD COLUMN product_id UUID NOT NULL,
    ADD COLUMN quantity NUMERIC NOT NULL,
    ADD COLUMN unit VARCHAR(32) NOT NULL,
    ADD CONSTRAINT inventory_items_quantity_check CHECK (quantity >= 0),
    ADD CONSTRAINT inventory_items_unit_check CHECK (unit IN ('GRAM', 'MILLILITER', 'PIECE')),
    ADD CONSTRAINT inventory_items_product_fk
        FOREIGN KEY (product_id) REFERENCES products (id);

CREATE INDEX inventory_items_product_id_idx ON inventory_items (product_id);
