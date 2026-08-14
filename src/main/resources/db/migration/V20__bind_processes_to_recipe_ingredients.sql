ALTER TABLE recipe_process_bindings
    ADD COLUMN recipe_ingredient_id UUID REFERENCES recipe_ingredients(id) ON DELETE CASCADE;

UPDATE recipe_process_bindings binding
SET recipe_ingredient_id = candidate.ingredient_id
FROM (
    SELECT binding.id AS binding_id, (ARRAY_AGG(ingredient.id))[1] AS ingredient_id
    FROM recipe_process_bindings binding
    JOIN recipe_steps step ON step.id = binding.recipe_step_id
    JOIN recipe_ingredients ingredient
      ON ingredient.recipe_id = step.recipe_id
     AND ingredient.product_template_id = binding.product_template_id
    GROUP BY binding.id
    HAVING COUNT(*) = 1
) candidate
WHERE binding.id = candidate.binding_id;

CREATE INDEX recipe_process_bindings_ingredient_idx
    ON recipe_process_bindings(recipe_ingredient_id);

ALTER TABLE recipe_template_process_bindings
    ADD COLUMN recipe_ingredient_id UUID REFERENCES recipe_template_ingredients(id) ON DELETE CASCADE;

UPDATE recipe_template_process_bindings binding
SET recipe_ingredient_id = candidate.ingredient_id
FROM (
    SELECT binding.id AS binding_id, (ARRAY_AGG(ingredient.id))[1] AS ingredient_id
    FROM recipe_template_process_bindings binding
    JOIN recipe_template_steps step ON step.id = binding.recipe_template_step_id
    JOIN recipe_template_ingredients ingredient
      ON ingredient.recipe_template_id = step.recipe_template_id
     AND ingredient.product_template_id = binding.product_template_id
    GROUP BY binding.id
    HAVING COUNT(*) = 1
) candidate
WHERE binding.id = candidate.binding_id;

CREATE INDEX recipe_template_process_bindings_ingredient_idx
    ON recipe_template_process_bindings(recipe_ingredient_id);
