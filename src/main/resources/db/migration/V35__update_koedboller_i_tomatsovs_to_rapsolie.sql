-- Upgrade only the deployed V32 meatball template from Neutral olie to Rapsolie.
-- Copied/user Recipes are intentionally untouched.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM recipe_template_ingredients
        WHERE id = '18943e10-2294-31d5-8612-e64d1f7864f5'
          AND recipe_template_id = 'f94ea16d-7040-3bbc-9432-3455cc0c9360'
          AND product_template_id = '4b63577c-a7ef-327a-acef-6ef6010b7d6a'
    ) THEN
        RAISE EXCEPTION 'Expected historical V32 Neutral olie ingredient is missing';
    END IF;

    IF EXISTS (
        SELECT 1 FROM recipe_template_ingredients
        WHERE id = 'ead0c449-549a-3bbc-85f4-7e822e817aa0'
    ) THEN
        RAISE EXCEPTION 'Current deterministic Rapsolie ingredient ID already exists';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM product_templates
        WHERE id = 'ce84c904-58c9-3c93-b8af-35eb4acd1499'
          AND name = 'Rapsolie'
    ) THEN
        RAISE EXCEPTION 'Canonical Rapsolie ProductTemplate is missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM recipe_template_process_bindings
        WHERE id = '89ded771-1aca-35ff-8a24-d6225c955c98'
          AND recipe_template_step_id = '69281b75-223d-32b9-b177-af1e93c39096'
          AND parameter_key = 'FAT'
          AND recipe_ingredient_id = '18943e10-2294-31d5-8612-e64d1f7864f5'
          AND product_template_id = '4b63577c-a7ef-327a-acef-6ef6010b7d6a'
    ) THEN
        RAISE EXCEPTION 'Expected historical V32 PAN_FRY_MEATBALLS FAT binding is missing';
    END IF;
END $$;

INSERT INTO recipe_template_ingredients(
    id, recipe_template_id, product_template_id, quantity, unit, preparation, sort_order
)
SELECT
    'ead0c449-549a-3bbc-85f4-7e822e817aa0',
    recipe_template_id,
    'ce84c904-58c9-3c93-b8af-35eb4acd1499',
    quantity,
    unit,
    preparation,
    sort_order + 1000
FROM recipe_template_ingredients
WHERE id = '18943e10-2294-31d5-8612-e64d1f7864f5'
  AND recipe_template_id = 'f94ea16d-7040-3bbc-9432-3455cc0c9360';

UPDATE recipe_template_process_bindings
SET recipe_ingredient_id = 'ead0c449-549a-3bbc-85f4-7e822e817aa0',
    product_template_id = 'ce84c904-58c9-3c93-b8af-35eb4acd1499'
WHERE id = '89ded771-1aca-35ff-8a24-d6225c955c98'
  AND recipe_template_step_id = '69281b75-223d-32b9-b177-af1e93c39096'
  AND parameter_key = 'FAT'
  AND recipe_ingredient_id = '18943e10-2294-31d5-8612-e64d1f7864f5'
  AND product_template_id = '4b63577c-a7ef-327a-acef-6ef6010b7d6a';

DELETE FROM recipe_template_ingredients
WHERE id = '18943e10-2294-31d5-8612-e64d1f7864f5'
  AND recipe_template_id = 'f94ea16d-7040-3bbc-9432-3455cc0c9360';

UPDATE recipe_template_ingredients
SET sort_order = 9
WHERE id = 'ead0c449-549a-3bbc-85f4-7e822e817aa0'
  AND recipe_template_id = 'f94ea16d-7040-3bbc-9432-3455cc0c9360';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM recipe_template_process_bindings
        WHERE id = '89ded771-1aca-35ff-8a24-d6225c955c98'
          AND recipe_ingredient_id = 'ead0c449-549a-3bbc-85f4-7e822e817aa0'
          AND product_template_id = 'ce84c904-58c9-3c93-b8af-35eb4acd1499'
    ) OR EXISTS (
        SELECT 1 FROM recipe_template_ingredients
        WHERE id = '18943e10-2294-31d5-8612-e64d1f7864f5'
    ) THEN
        RAISE EXCEPTION 'Meatball template Rapsolie upgrade did not reach the canonical graph';
    END IF;
END $$;
