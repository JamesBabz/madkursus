ALTER TABLE recipe_ingredients DROP CONSTRAINT recipe_ingredients_unit_check;
ALTER TABLE recipe_ingredients ADD CONSTRAINT recipe_ingredients_unit_check
    CHECK (unit IN ('GRAM','MILLILITER','PIECE','TEASPOON','TABLESPOON','DECILITER','GRINDER_TURN'));

ALTER TABLE recipe_template_ingredients DROP CONSTRAINT recipe_template_ingredients_unit_check;
ALTER TABLE recipe_template_ingredients ADD CONSTRAINT recipe_template_ingredients_unit_check
    CHECK (unit IN ('GRAM','MILLILITER','PIECE','TEASPOON','TABLESPOON','DECILITER','GRINDER_TURN'));

ALTER TABLE cooking_process_parameters DROP CONSTRAINT cooking_process_parameters_unit_check;
ALTER TABLE cooking_process_parameters ADD CONSTRAINT cooking_process_parameters_unit_check
    CHECK (unit IS NULL OR unit IN ('GRAM','MILLILITER','PIECE','TEASPOON','TABLESPOON','DECILITER','GRINDER_TURN'));

ALTER TABLE cooking_process_parameters DROP CONSTRAINT cooking_process_parameters_default_unit_check;
ALTER TABLE cooking_process_parameters ADD CONSTRAINT cooking_process_parameters_default_unit_check
    CHECK (default_unit IS NULL OR default_unit IN ('GRAM','MILLILITER','PIECE','TEASPOON','TABLESPOON','DECILITER','GRINDER_TURN'));

ALTER TABLE recipe_process_bindings DROP CONSTRAINT recipe_process_bindings_unit_check;
ALTER TABLE recipe_process_bindings ADD CONSTRAINT recipe_process_bindings_unit_check
    CHECK (unit IS NULL OR unit IN ('GRAM','MILLILITER','PIECE','TEASPOON','TABLESPOON','DECILITER','GRINDER_TURN'));

ALTER TABLE recipe_template_process_bindings DROP CONSTRAINT recipe_template_process_bindings_unit_check;
ALTER TABLE recipe_template_process_bindings ADD CONSTRAINT recipe_template_process_bindings_unit_check
    CHECK (unit IS NULL OR unit IN ('GRAM','MILLILITER','PIECE','TEASPOON','TABLESPOON','DECILITER','GRINDER_TURN'));
