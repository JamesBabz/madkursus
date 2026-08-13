CREATE TABLE recipes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(255) NOT NULL CHECK (LENGTH(TRIM(name)) > 0),
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX recipes_user_updated_idx ON recipes(user_id, updated_at DESC);

CREATE TABLE recipe_ingredients (
    id UUID PRIMARY KEY,
    recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    product_template_id UUID NOT NULL REFERENCES product_templates(id),
    quantity NUMERIC NOT NULL CHECK (quantity > 0),
    unit VARCHAR(16) NOT NULL CHECK (unit IN ('GRAM','MILLILITER','PIECE','TEASPOON','TABLESPOON','DECILITER')),
    preparation TEXT,
    sort_order INTEGER NOT NULL CHECK (sort_order > 0),
    UNIQUE(recipe_id, sort_order)
);
CREATE INDEX recipe_ingredients_recipe_order_idx ON recipe_ingredients(recipe_id, sort_order);

CREATE TABLE recipe_steps (
    id UUID PRIMARY KEY,
    recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    instruction TEXT NOT NULL CHECK (LENGTH(TRIM(instruction)) > 0),
    sort_order INTEGER NOT NULL CHECK (sort_order > 0),
    UNIQUE(recipe_id, sort_order)
);
CREATE INDEX recipe_steps_recipe_order_idx ON recipe_steps(recipe_id, sort_order);
