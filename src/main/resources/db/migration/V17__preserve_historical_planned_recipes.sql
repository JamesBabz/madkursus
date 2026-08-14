ALTER TABLE planned_recipes ADD COLUMN recipe_name VARCHAR(255);

UPDATE planned_recipes planned
SET recipe_name = recipe.name
FROM recipes recipe
WHERE planned.recipe_id = recipe.id;

ALTER TABLE planned_recipes ALTER COLUMN recipe_name SET NOT NULL;
ALTER TABLE planned_recipes ALTER COLUMN recipe_id DROP NOT NULL;

-- The existing restrictive FK deliberately remains. Application deletion first detaches
-- COOKED/SKIPPED rows; any concurrent PLANNED reference still protects the live recipe.
