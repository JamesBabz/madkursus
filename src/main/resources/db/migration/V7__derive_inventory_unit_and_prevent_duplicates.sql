ALTER TABLE inventory_items DROP CONSTRAINT inventory_items_unit_check;
ALTER TABLE inventory_items DROP COLUMN unit;

-- Historical duplicates are merged without losing stock before enforcing one row per Product.
WITH totals AS (
    SELECT product_id, MIN(id::text)::uuid AS keep_id, SUM(quantity) AS total
    FROM inventory_items GROUP BY product_id
), updated AS (
    UPDATE inventory_items i SET quantity = t.total
    FROM totals t WHERE i.id = t.keep_id RETURNING i.id
)
DELETE FROM inventory_items i USING totals t
WHERE i.product_id = t.product_id AND i.id <> t.keep_id;

ALTER TABLE inventory_items ADD CONSTRAINT inventory_items_product_unique UNIQUE(product_id);
