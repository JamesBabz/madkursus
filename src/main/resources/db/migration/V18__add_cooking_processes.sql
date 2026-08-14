CREATE TABLE cooking_processes (
    id UUID PRIMARY KEY,
    process_key VARCHAR(100) NOT NULL UNIQUE CHECK (process_key ~ '^[A-Z][A-Z0-9_]*$'),
    name VARCHAR(255) NOT NULL CHECK (LENGTH(TRIM(name)) > 0),
    description TEXT,
    completion_criteria_template TEXT NOT NULL CHECK (LENGTH(TRIM(completion_criteria_template)) > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX cooking_processes_active_name_idx ON cooking_processes(active, name);

CREATE TABLE cooking_process_parameters (
    id UUID PRIMARY KEY,
    cooking_process_id UUID NOT NULL REFERENCES cooking_processes(id) ON DELETE CASCADE,
    parameter_key VARCHAR(100) NOT NULL CHECK (parameter_key ~ '^[A-Z][A-Z0-9_]*$'),
    label VARCHAR(255) NOT NULL,
    parameter_type VARCHAR(32) NOT NULL CHECK (parameter_type IN ('INGREDIENT_QUANTITY','QUANTITY','DURATION','TEMPERATURE','HEAT_LEVEL','NUMBER','TEXT')),
    required BOOLEAN NOT NULL,
    unit VARCHAR(16) CHECK (unit IN ('GRAM','MILLILITER','PIECE','TEASPOON','TABLESPOON','DECILITER')),
    default_quantity NUMERIC,
    default_unit VARCHAR(16) CHECK (default_unit IN ('GRAM','MILLILITER','PIECE','TEASPOON','TABLESPOON','DECILITER')),
    default_duration_seconds INTEGER CHECK (default_duration_seconds > 0),
    default_temperature_celsius INTEGER,
    default_heat_level VARCHAR(20) CHECK (default_heat_level IN ('LOW','MEDIUM_LOW','MEDIUM','MEDIUM_HIGH','HIGH','MAX')),
    default_number NUMERIC,
    default_text TEXT,
    sort_order INTEGER NOT NULL CHECK (sort_order > 0),
    UNIQUE(cooking_process_id, parameter_key),
    UNIQUE(cooking_process_id, sort_order)
);

CREATE TABLE cooking_process_steps (
    id UUID PRIMARY KEY,
    cooking_process_id UUID NOT NULL REFERENCES cooking_processes(id) ON DELETE CASCADE,
    instruction_template TEXT NOT NULL CHECK (LENGTH(TRIM(instruction_template)) > 0),
    sort_order INTEGER NOT NULL CHECK (sort_order > 0),
    UNIQUE(cooking_process_id, sort_order)
);

CREATE TABLE cooking_process_equipment_requirements (
    id UUID PRIMARY KEY,
    cooking_process_id UUID NOT NULL REFERENCES cooking_processes(id) ON DELETE CASCADE,
    equipment_type VARCHAR(32) NOT NULL CHECK (equipment_type IN ('STOVE','OVEN','POT','PAN','AIR_FRYER','THERMOMETER','MICROWAVE')),
    requirement_level VARCHAR(16) NOT NULL CHECK (requirement_level IN ('REQUIRED','RECOMMENDED')),
    UNIQUE(cooking_process_id, equipment_type)
);

ALTER TABLE recipe_steps ALTER COLUMN instruction DROP NOT NULL;
ALTER TABLE recipe_steps ADD COLUMN step_type VARCHAR(16) NOT NULL DEFAULT 'TEXT' CHECK (step_type IN ('TEXT','PROCESS'));
ALTER TABLE recipe_steps ADD COLUMN cooking_process_id UUID REFERENCES cooking_processes(id);
ALTER TABLE recipe_steps ADD CONSTRAINT recipe_steps_content_check CHECK (
    (step_type='TEXT' AND instruction IS NOT NULL AND LENGTH(TRIM(instruction))>0 AND cooking_process_id IS NULL)
    OR (step_type='PROCESS' AND instruction IS NULL AND cooking_process_id IS NOT NULL)
);

CREATE TABLE recipe_process_bindings (
    id UUID PRIMARY KEY,
    recipe_step_id UUID NOT NULL REFERENCES recipe_steps(id) ON DELETE CASCADE,
    parameter_key VARCHAR(100) NOT NULL,
    product_template_id UUID REFERENCES product_templates(id),
    quantity NUMERIC,
    unit VARCHAR(16) CHECK (unit IN ('GRAM','MILLILITER','PIECE','TEASPOON','TABLESPOON','DECILITER')),
    duration_seconds INTEGER CHECK (duration_seconds > 0),
    temperature_celsius INTEGER,
    heat_level VARCHAR(20) CHECK (heat_level IN ('LOW','MEDIUM_LOW','MEDIUM','MEDIUM_HIGH','HIGH','MAX')),
    number_value NUMERIC,
    text_value TEXT,
    UNIQUE(recipe_step_id, parameter_key)
);

ALTER TABLE recipe_template_steps ALTER COLUMN instruction DROP NOT NULL;
ALTER TABLE recipe_template_steps ADD COLUMN step_type VARCHAR(16) NOT NULL DEFAULT 'TEXT' CHECK (step_type IN ('TEXT','PROCESS'));
ALTER TABLE recipe_template_steps ADD COLUMN cooking_process_id UUID REFERENCES cooking_processes(id);
ALTER TABLE recipe_template_steps ADD CONSTRAINT recipe_template_steps_content_check CHECK (
    (step_type='TEXT' AND instruction IS NOT NULL AND LENGTH(TRIM(instruction))>0 AND cooking_process_id IS NULL)
    OR (step_type='PROCESS' AND instruction IS NULL AND cooking_process_id IS NOT NULL)
);

CREATE TABLE recipe_template_process_bindings (
    id UUID PRIMARY KEY,
    recipe_template_step_id UUID NOT NULL REFERENCES recipe_template_steps(id) ON DELETE CASCADE,
    parameter_key VARCHAR(100) NOT NULL,
    product_template_id UUID REFERENCES product_templates(id),
    quantity NUMERIC,
    unit VARCHAR(16) CHECK (unit IN ('GRAM','MILLILITER','PIECE','TEASPOON','TABLESPOON','DECILITER')),
    duration_seconds INTEGER CHECK (duration_seconds > 0),
    temperature_celsius INTEGER,
    heat_level VARCHAR(20) CHECK (heat_level IN ('LOW','MEDIUM_LOW','MEDIUM','MEDIUM_HIGH','HIGH','MAX')),
    number_value NUMERIC,
    text_value TEXT,
    UNIQUE(recipe_template_step_id, parameter_key)
);
