CREATE TABLE dtu_reference_foods (
    dataset_version VARCHAR(40) NOT NULL,
    food_id VARCHAR(40) NOT NULL,
    danish_name VARCHAR(500) NOT NULL,
    normalized_name VARCHAR(500) NOT NULL,
    carbohydrate_grams NUMERIC NOT NULL CHECK (carbohydrate_grams >= 0),
    basis_quantity NUMERIC NOT NULL DEFAULT 100 CHECK (basis_quantity > 0),
    basis_unit VARCHAR(32) NOT NULL DEFAULT 'GRAM' CHECK (basis_unit IN ('GRAM')),
    state_description VARCHAR(250),
    source_date DATE,
    source_url VARCHAR(1000) NOT NULL,
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (dataset_version, food_id)
);

CREATE INDEX dtu_reference_foods_name_idx
    ON dtu_reference_foods (dataset_version, normalized_name);

CREATE TABLE product_template_dtu_mappings (
    product_template_id UUID PRIMARY KEY REFERENCES product_templates(id) ON DELETE CASCADE,
    dataset_version VARCHAR(40) NOT NULL,
    food_id VARCHAR(40) NOT NULL,
    approved_carbohydrate_grams NUMERIC NOT NULL CHECK (approved_carbohydrate_grams >= 0),
    approved_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (dataset_version, food_id)
        REFERENCES dtu_reference_foods(dataset_version, food_id)
);

COMMENT ON TABLE dtu_reference_foods IS
    'Imported, versioned DTU Frida reference data. It is not runtime recipe nutrition.';
COMMENT ON TABLE product_template_dtu_mappings IS
    'Explicitly admin-approved ProductTemplate to DTU reference mappings.';
