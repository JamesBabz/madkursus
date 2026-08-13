CREATE TABLE recipe_templates (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    normalized_name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX recipe_templates_active_name_idx ON recipe_templates(active, normalized_name);

CREATE TABLE recipe_template_ingredients (
    id UUID PRIMARY KEY,
    recipe_template_id UUID NOT NULL REFERENCES recipe_templates(id) ON DELETE CASCADE,
    product_template_id UUID NOT NULL REFERENCES product_templates(id),
    quantity NUMERIC NOT NULL CHECK (quantity > 0),
    unit VARCHAR(16) NOT NULL CHECK (unit IN ('GRAM','MILLILITER','PIECE','TEASPOON','TABLESPOON','DECILITER')),
    preparation TEXT,
    sort_order INTEGER NOT NULL CHECK (sort_order > 0),
    UNIQUE(recipe_template_id, sort_order)
);
CREATE INDEX recipe_template_ingredients_order_idx ON recipe_template_ingredients(recipe_template_id, sort_order);

CREATE TABLE recipe_template_steps (
    id UUID PRIMARY KEY,
    recipe_template_id UUID NOT NULL REFERENCES recipe_templates(id) ON DELETE CASCADE,
    instruction TEXT NOT NULL CHECK (LENGTH(TRIM(instruction)) > 0),
    sort_order INTEGER NOT NULL CHECK (sort_order > 0),
    UNIQUE(recipe_template_id, sort_order)
);
CREATE INDEX recipe_template_steps_order_idx ON recipe_template_steps(recipe_template_id, sort_order);

ALTER TABLE recipes ADD COLUMN source_template_id UUID REFERENCES recipe_templates(id) ON DELETE SET NULL;
CREATE UNIQUE INDEX recipes_user_source_template_unique ON recipes(user_id, source_template_id) WHERE source_template_id IS NOT NULL;
