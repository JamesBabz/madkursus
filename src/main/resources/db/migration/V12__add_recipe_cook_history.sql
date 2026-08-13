CREATE TABLE recipe_cook_history (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    recipe_id UUID REFERENCES recipes(id) ON DELETE SET NULL,
    recipe_name VARCHAR(255) NOT NULL,
    portions NUMERIC NOT NULL CHECK (portions > 0),
    cooked_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX recipe_cook_history_user_cooked_idx ON recipe_cook_history(user_id, cooked_at DESC);
CREATE INDEX recipe_cook_history_recipe_idx ON recipe_cook_history(recipe_id);
