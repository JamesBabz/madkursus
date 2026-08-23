CREATE TABLE recipe_prepared_components (
 id UUID PRIMARY KEY, recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
 component_key VARCHAR(100) NOT NULL, name VARCHAR(255) NOT NULL, sort_order INTEGER NOT NULL CHECK(sort_order>0),
 UNIQUE(recipe_id,component_key),UNIQUE(recipe_id,sort_order));
CREATE TABLE recipe_prepared_component_ingredients (
 id UUID PRIMARY KEY,prepared_component_id UUID NOT NULL REFERENCES recipe_prepared_components(id) ON DELETE CASCADE,
 recipe_ingredient_id UUID NOT NULL REFERENCES recipe_ingredients(id),quantity NUMERIC NOT NULL CHECK(quantity>0),unit VARCHAR(16) NOT NULL,
 sort_order INTEGER NOT NULL CHECK(sort_order>0),UNIQUE(prepared_component_id,recipe_ingredient_id));
CREATE TABLE recipe_template_prepared_components (
 id UUID PRIMARY KEY,recipe_template_id UUID NOT NULL REFERENCES recipe_templates(id) ON DELETE CASCADE,
 component_key VARCHAR(100) NOT NULL,name VARCHAR(255) NOT NULL,sort_order INTEGER NOT NULL CHECK(sort_order>0),
 UNIQUE(recipe_template_id,component_key),UNIQUE(recipe_template_id,sort_order));
CREATE TABLE recipe_template_prepared_component_ingredients (
 id UUID PRIMARY KEY,prepared_component_id UUID NOT NULL REFERENCES recipe_template_prepared_components(id) ON DELETE CASCADE,
 recipe_ingredient_id UUID NOT NULL REFERENCES recipe_template_ingredients(id),quantity NUMERIC NOT NULL CHECK(quantity>0),unit VARCHAR(16) NOT NULL,
 sort_order INTEGER NOT NULL CHECK(sort_order>0),UNIQUE(prepared_component_id,recipe_ingredient_id));
ALTER TABLE recipe_preparation_steps ADD COLUMN prepared_component_id UUID REFERENCES recipe_prepared_components(id) ON DELETE SET NULL;
ALTER TABLE recipe_template_preparation_steps ADD COLUMN prepared_component_id UUID REFERENCES recipe_template_prepared_components(id) ON DELETE SET NULL;
ALTER TABLE recipe_process_bindings ADD COLUMN prepared_component_id UUID REFERENCES recipe_prepared_components(id) ON DELETE SET NULL;
ALTER TABLE recipe_template_process_bindings ADD COLUMN prepared_component_id UUID REFERENCES recipe_template_prepared_components(id) ON DELETE SET NULL;
