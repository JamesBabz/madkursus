ALTER TABLE product_templates
    ADD COLUMN carbohydrate_grams NUMERIC,
    ADD COLUMN nutrition_basis_quantity NUMERIC,
    ADD COLUMN nutrition_basis_unit VARCHAR(32),
    ADD COLUMN nutrition_source VARCHAR(500),
    ADD CONSTRAINT product_templates_nutrition_complete CHECK (
        (carbohydrate_grams IS NULL AND nutrition_basis_quantity IS NULL AND nutrition_basis_unit IS NULL)
        OR (carbohydrate_grams >= 0 AND nutrition_basis_quantity > 0
            AND nutrition_basis_unit IN ('GRAM','MILLILITER','PIECE'))
    );

-- Curated starter values use Frida's "Tilgængelig kulhydrat, deklaration" where available.
-- Product-specific packaged-food data may replace these internal values in a later additive migration.
UPDATE product_templates SET carbohydrate_grams=73.9, nutrition_basis_quantity=100,
    nutrition_basis_unit='GRAM', nutrition_source='DTU Frida food 305, Pasta, rå; accessed 2026-08-25'
WHERE id='99f6dde2-0756-3f15-b72c-5fa5fe69d936';

UPDATE product_templates SET carbohydrate_grams=16.7, nutrition_basis_quantity=100,
    nutrition_basis_unit='GRAM', nutrition_source='DTU Frida food 4, Kartoffel, uspec., rå; accessed 2026-08-25'
WHERE id='4d130604-de3d-3289-ae2f-0391815e028b';

UPDATE product_templates SET carbohydrate_grams=0, nutrition_basis_quantity=100,
    nutrition_basis_unit='MILLILITER', nutrition_source='Known zero: potable water'
WHERE id='04a53a53-364c-373c-8fe9-68e4146652d4';
