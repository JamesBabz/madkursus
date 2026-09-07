ALTER TABLE product_templates
    ADD COLUMN nutrition_provider VARCHAR(100),
    ADD COLUMN nutrition_external_food_id VARCHAR(150),
    ADD COLUMN nutrition_source_version VARCHAR(100),
    ADD COLUMN nutrition_source_url VARCHAR(1000),
    ADD COLUMN nutrition_note VARCHAR(1000);

UPDATE product_templates SET nutrition_provider='DTU', nutrition_external_food_id='305'
WHERE id='99f6dde2-0756-3f15-b72c-5fa5fe69d936';
UPDATE product_templates SET nutrition_provider='DTU', nutrition_external_food_id='4'
WHERE id='4d130604-de3d-3289-ae2f-0391815e028b';
