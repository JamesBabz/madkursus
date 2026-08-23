ALTER TABLE cooking_processes
    ADD COLUMN active_duration_seconds INTEGER CHECK (active_duration_seconds >= 0),
    ADD COLUMN passive_duration_seconds INTEGER CHECK (passive_duration_seconds >= 0),
    ADD COLUMN active_duration_parameter_key VARCHAR(100),
    ADD COLUMN passive_duration_parameter_key VARCHAR(100);

CREATE TABLE cooking_process_preparation_requirements (
    id UUID PRIMARY KEY,
    cooking_process_id UUID NOT NULL REFERENCES cooking_processes(id) ON DELETE CASCADE,
    parameter_key VARCHAR(100) NOT NULL,
    instruction_template TEXT NOT NULL CHECK (LENGTH(TRIM(instruction_template)) > 0),
    sort_order INTEGER NOT NULL CHECK (sort_order > 0),
    UNIQUE(cooking_process_id, sort_order)
);

UPDATE cooking_processes SET active_duration_seconds=300, passive_duration_parameter_key='SIMMER_TIME' WHERE process_key='BOIL_POTATOES';
UPDATE cooking_processes SET active_duration_seconds=120, passive_duration_parameter_key='COOK_TIME' WHERE process_key='BOIL_PASTA';
UPDATE cooking_processes SET active_duration_parameter_key='MIX_TIME', passive_duration_seconds=0 WHERE process_key='MIX_MEATBALL_MIXTURE';
UPDATE cooking_processes SET active_duration_seconds=600, passive_duration_seconds=0 WHERE process_key='PAN_FRY_CHICKEN_BREAST';
UPDATE cooking_processes SET active_duration_seconds=120, passive_duration_parameter_key='COOK_TIME' WHERE process_key='BOIL_RICE';
UPDATE cooking_processes SET active_duration_seconds=180, passive_duration_seconds=0 WHERE process_key='PAN_FRY_MEATBALLS';
UPDATE cooking_process_steps SET instruction_template='Tilsæt {SALT} salt.'
WHERE cooking_process_id=(SELECT id FROM cooking_processes WHERE process_key='BOIL_POTATOES')
  AND instruction_template='Tilsæt salt efter smag.';

INSERT INTO cooking_process_preparation_requirements(id,cooking_process_id,parameter_key,instruction_template,sort_order)
SELECT md5(process_key||':prep:BASE')::uuid,id,'BASE','Mål {BASE} op.',1 FROM cooking_processes WHERE process_key='MIX_MEATBALL_MIXTURE';
INSERT INTO cooking_process_preparation_requirements(id,cooking_process_id,parameter_key,instruction_template,sort_order)
SELECT md5(process_key||':prep:ADDITIONS')::uuid,id,'ADDITIONS','Mål {ADDITIONS} op.',2 FROM cooking_processes WHERE process_key='MIX_MEATBALL_MIXTURE';
INSERT INTO cooking_process_preparation_requirements(id,cooking_process_id,parameter_key,instruction_template,sort_order)
SELECT md5(process_key||':prep:CHICKEN')::uuid,id,'CHICKEN','Dup {CHICKEN} tør.',1 FROM cooking_processes WHERE process_key='PAN_FRY_CHICKEN_BREAST';
