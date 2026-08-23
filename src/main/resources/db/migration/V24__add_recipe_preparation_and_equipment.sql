ALTER TABLE cooking_process_parameters DROP CONSTRAINT cooking_process_parameters_parameter_type_check;
ALTER TABLE cooking_process_parameters ADD CONSTRAINT cooking_process_parameters_parameter_type_check
    CHECK (parameter_type IN ('INGREDIENT_QUANTITY','INGREDIENT_LIST','QUANTITY','DURATION','TEMPERATURE','HEAT_LEVEL','NUMBER','TEXT'));

CREATE TABLE recipe_preparation_steps (
    id UUID PRIMARY KEY, recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    instruction TEXT NOT NULL CHECK (length(trim(instruction))>0), sort_order INTEGER NOT NULL CHECK(sort_order>0),
    UNIQUE(recipe_id,sort_order)
);
CREATE TABLE recipe_template_preparation_steps (
    id UUID PRIMARY KEY, recipe_template_id UUID NOT NULL REFERENCES recipe_templates(id) ON DELETE CASCADE,
    instruction TEXT NOT NULL CHECK (length(trim(instruction))>0), sort_order INTEGER NOT NULL CHECK(sort_order>0),
    UNIQUE(recipe_template_id,sort_order)
);
CREATE TABLE recipe_equipment_requirements (
    id UUID PRIMARY KEY, recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    equipment_type VARCHAR(32), label VARCHAR(255), sort_order INTEGER NOT NULL CHECK(sort_order>0),
    CHECK (equipment_type IS NOT NULL OR length(trim(label))>0), UNIQUE(recipe_id,sort_order)
);
CREATE TABLE recipe_template_equipment_requirements (
    id UUID PRIMARY KEY, recipe_template_id UUID NOT NULL REFERENCES recipe_templates(id) ON DELETE CASCADE,
    equipment_type VARCHAR(32), label VARCHAR(255), sort_order INTEGER NOT NULL CHECK(sort_order>0),
    CHECK (equipment_type IS NOT NULL OR length(trim(label))>0), UNIQUE(recipe_template_id,sort_order)
);
