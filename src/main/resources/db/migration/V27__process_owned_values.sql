ALTER TABLE cooking_process_parameters
    ADD COLUMN value_source VARCHAR(32) NOT NULL DEFAULT 'INPUT',
    ADD COLUMN derived_rule VARCHAR(64),
    ADD COLUMN derived_from VARCHAR(100);

ALTER TABLE cooking_process_parameters ADD CONSTRAINT cooking_process_parameter_source_check
    CHECK (value_source IN ('INPUT','DEFAULT','OVERRIDEABLE_DEFAULT','DERIVED'));

-- Existing bindings are preserved. Definitions below make them harmless legacy overrides;
-- new and edited usages only persist recipe inputs and explicit overrides.
UPDATE cooking_process_parameters SET value_source='OVERRIDEABLE_DEFAULT'
WHERE default_quantity IS NOT NULL OR default_duration_seconds IS NOT NULL OR default_temperature_celsius IS NOT NULL
   OR default_heat_level IS NOT NULL OR default_number IS NOT NULL OR default_text IS NOT NULL;

UPDATE cooking_process_parameters SET value_source='DERIVED', derived_from='POTATOES', derived_rule='POTATO_WATER_PER_GRAM'
WHERE cooking_process_id=(SELECT id FROM cooking_processes WHERE process_key='BOIL_POTATOES') AND parameter_key='WATER';
UPDATE cooking_process_parameters SET value_source='DERIVED', derived_from='POTATOES', derived_rule='POTATO_SALT_PER_GRAM'
WHERE cooking_process_id=(SELECT id FROM cooking_processes WHERE process_key='BOIL_POTATOES') AND parameter_key='SALT';
UPDATE cooking_process_parameters SET value_source='DERIVED', derived_from='PASTA', derived_rule='PASTA_WATER_PER_GRAM'
WHERE cooking_process_id=(SELECT id FROM cooking_processes WHERE process_key='BOIL_PASTA') AND parameter_key='WATER';
UPDATE cooking_process_parameters SET value_source='DERIVED', derived_from='PASTA', derived_rule='PASTA_SALT_PER_GRAM'
WHERE cooking_process_id=(SELECT id FROM cooking_processes WHERE process_key='BOIL_PASTA') AND parameter_key='SALT';
UPDATE cooking_process_parameters SET value_source='DERIVED', derived_from='RICE', derived_rule='RICE_WATER_PER_GRAM'
WHERE cooking_process_id=(SELECT id FROM cooking_processes WHERE process_key='BOIL_RICE') AND parameter_key='WATER';

UPDATE cooking_process_parameters SET value_source='INPUT', required=true
WHERE parameter_key IN ('POTATOES','PASTA','RICE','CHICKEN','MEAT','ONION','VEGETABLES','SOUP','FLOUR','LIQUID','BUTTER','FAT')
  AND default_quantity IS NULL AND default_duration_seconds IS NULL AND default_temperature_celsius IS NULL
  AND default_heat_level IS NULL AND default_number IS NULL AND default_text IS NULL;
UPDATE cooking_process_parameters SET required=false WHERE value_source<>'INPUT';

UPDATE cooking_process_parameters SET parameter_type='QUANTITY', unit='TEASPOON'
WHERE value_source='DERIVED' AND parameter_key='SALT';

UPDATE cooking_process_parameters SET parameter_key='BASE', label='Fars', value_source='INPUT', required=true
WHERE cooking_process_id=(SELECT id FROM cooking_processes WHERE process_key='MIX_MEATBALL_MIXTURE') AND parameter_key='MEAT';
DELETE FROM cooking_process_parameters WHERE cooking_process_id=(SELECT id FROM cooking_processes WHERE process_key='MIX_MEATBALL_MIXTURE')
  AND parameter_key IN ('EGG','ONION','BINDER','LIQUID','SALT','PEPPER','OTHER_SEASONING');
UPDATE cooking_process_parameters SET parameter_key='ADDITIONS', label='Ingredienser i farsen', value_source='INPUT', required=false
WHERE cooking_process_id=(SELECT id FROM cooking_processes WHERE process_key='MIX_MEATBALL_MIXTURE') AND parameter_key='INGREDIENTS';
UPDATE recipe_template_process_bindings b SET parameter_key=CASE WHEN parameter_key='MEAT' THEN 'BASE' ELSE 'ADDITIONS:'||b.id::text END
FROM recipe_template_steps s, cooking_processes p WHERE b.recipe_template_step_id=s.id AND s.cooking_process_id=p.id AND p.process_key='MIX_MEATBALL_MIXTURE' AND (parameter_key='MEAT' OR parameter_key IN ('EGG','ONION','BINDER','LIQUID','SALT','PEPPER','OTHER_SEASONING'));
UPDATE recipe_process_bindings b SET parameter_key=CASE WHEN parameter_key='MEAT' THEN 'BASE' ELSE 'ADDITIONS:'||b.id::text END
FROM recipe_steps s, cooking_processes p WHERE b.recipe_step_id=s.id AND s.cooking_process_id=p.id AND p.process_key='MIX_MEATBALL_MIXTURE' AND (parameter_key='MEAT' OR parameter_key IN ('EGG','ONION','BINDER','LIQUID','SALT','PEPPER','OTHER_SEASONING'));
UPDATE cooking_process_steps SET instruction_template='Kom {BASE} og {ADDITIONS} i en skål.' WHERE cooking_process_id=(SELECT id FROM cooking_processes WHERE process_key='MIX_MEATBALL_MIXTURE') AND sort_order=1;
