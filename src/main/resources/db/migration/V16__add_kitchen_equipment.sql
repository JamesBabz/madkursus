CREATE TABLE kitchen_equipment (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    equipment_type VARCHAR(32) NOT NULL CHECK (equipment_type IN (
        'STOVE','OVEN','POT','PAN','AIR_FRYER','THERMOMETER','MICROWAVE')),
    name VARCHAR(255) NOT NULL CHECK (LENGTH(TRIM(name)) > 0),
    normalized_name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    preferred BOOLEAN NOT NULL DEFAULT FALSE,
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(user_id, equipment_type, normalized_name)
);

CREATE INDEX kitchen_equipment_user_type_idx
    ON kitchen_equipment(user_id, equipment_type, active);
CREATE UNIQUE INDEX kitchen_equipment_user_preferred_type_unique
    ON kitchen_equipment(user_id, equipment_type)
    WHERE preferred = TRUE;
