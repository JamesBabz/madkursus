ALTER TABLE recipe_prepared_components
    ADD COLUMN preparation_steps JSONB NOT NULL DEFAULT '[]'::jsonb;
