CREATE TABLE meal_plans (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(255) NOT NULL CHECK (LENGTH(TRIM(name)) > 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX meal_plans_user_updated_idx ON meal_plans(user_id, updated_at DESC);

CREATE TABLE planned_recipes (
    id UUID PRIMARY KEY,
    meal_plan_id UUID NOT NULL REFERENCES meal_plans(id) ON DELETE CASCADE,
    recipe_id UUID NOT NULL REFERENCES recipes(id),
    portions INTEGER NOT NULL CHECK (portions > 0),
    sort_order INTEGER NOT NULL CHECK (sort_order > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PLANNED','COOKED','SKIPPED')),
    UNIQUE(meal_plan_id, sort_order)
);
CREATE INDEX planned_recipes_plan_order_idx ON planned_recipes(meal_plan_id, sort_order);
CREATE INDEX planned_recipes_recipe_status_idx ON planned_recipes(recipe_id, status);
