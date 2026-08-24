ALTER TABLE recipe_preparation_steps ADD COLUMN structured_instruction JSONB;
ALTER TABLE recipe_template_preparation_steps ADD COLUMN structured_instruction JSONB;
ALTER TABLE recipe_steps ADD COLUMN structured_instruction JSONB;
ALTER TABLE recipe_template_steps ADD COLUMN structured_instruction JSONB;
