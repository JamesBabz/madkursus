-- Generated locally. Review and test before deployment.
-- Existing copied Recipes are intentionally untouched.
BEGIN;
INSERT INTO recipe_templates(id,name,normalized_name,description,active,created_at,updated_at) VALUES ('d64a0705-7720-37d2-8936-131a1dc6444b','Simpel fluffy æggekage med bacon','simpel fluffy æggekage med bacon','Blød og fluffy æggekage stegt langsomt på panden og serveret med sprødstegt bacon.',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON CONFLICT(id) DO UPDATE SET name=EXCLUDED.name,normalized_name=EXCLUDED.normalized_name,description=EXCLUDED.description,active=true,updated_at=CURRENT_TIMESTAMP;
INSERT INTO recipe_template_equipment_requirements(id,recipe_template_id,equipment_type,label,sort_order) VALUES ('8ab96704-323c-3412-ab5a-8503981e2397','d64a0705-7720-37d2-8936-131a1dc6444b','STOVE',NULL,1);
INSERT INTO recipe_template_equipment_requirements(id,recipe_template_id,equipment_type,label,sort_order) VALUES ('eec7273b-bdb3-316b-9a9a-109e1e648fb0','d64a0705-7720-37d2-8936-131a1dc6444b','PAN',NULL,2);
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'dae80f16-98bd-366f-a567-fc646c240a31','d64a0705-7720-37d2-8936-131a1dc6444b',id,3,'PIECE',NULL,1 FROM product_templates WHERE id='60e27233-16b9-395f-8aac-ad23cc1209a4';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'a4fd26bd-f29a-3d4c-8731-6f11aa9b8d8d','d64a0705-7720-37d2-8936-131a1dc6444b',id,75,'GRAM',NULL,2 FROM product_templates WHERE id='c3194e88-aef3-3aaa-a9c5-6cc747847669';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '34199736-8cbd-32c2-b3b6-3a66594bfd9c','d64a0705-7720-37d2-8936-131a1dc6444b',id,45,'MILLILITER',NULL,3 FROM product_templates WHERE id='bfe6512e-55cb-39bd-a8c8-89f6a068285d';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '564f078e-558c-3aa1-b414-8530b3ac5b21','d64a0705-7720-37d2-8936-131a1dc6444b',id,0.25,'TEASPOON',NULL,4 FROM product_templates WHERE id='f960beee-dc58-3e81-b4e2-da7f1feb354e';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '980d4cfe-7a20-355e-aea1-32976c6b8be4','d64a0705-7720-37d2-8936-131a1dc6444b',id,5,'GRINDER_TURN',NULL,5 FROM product_templates WHERE id='2c65e27d-5cac-386d-a8e2-b56ccf62205f';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '24ee8cfb-f81d-3b59-bcb4-52f17f75f5c6','d64a0705-7720-37d2-8936-131a1dc6444b',id,5,'GRAM',NULL,6 FROM product_templates WHERE id='40867ff0-8b5e-3e4b-a4c2-67596c15aef6';
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('e129a12f-7a82-32f4-a528-623c9e24c7af','d64a0705-7720-37d2-8936-131a1dc6444b',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Slå "
  }, {
    "recipeIngredientId" : "dae80f16-98bd-366f-a567-fc646c240a31"
  }, {
    "text" : " ud i en skål. Tilsæt "
  }, {
    "recipeIngredientId" : "34199736-8cbd-32c2-b3b6-3a66594bfd9c"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "564f078e-558c-3aa1-b414-8530b3ac5b21"
  }, {
    "text" : " og "
  }, {
    "recipeIngredientId" : "980d4cfe-7a20-355e-aea1-32976c6b8be4"
  }, {
    "text" : ". Pisk i 30 sekunder, til blandingen er ensartet."
  } ]
}'::jsonb,1);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('77dabbac-9694-3963-a153-d9ca9aa1460c','d64a0705-7720-37d2-8936-131a1dc6444b','[structured instruction]','{
  "parts" : [ {
    "text" : "Sæt en tør, kold pande på 6/9. Læg "
  }, {
    "recipeIngredientId" : "a4fd26bd-f29a-3d4c-8731-6f11aa9b8d8d"
  }, {
    "text" : " på panden i hele skiver. Steg 4 minutter på første side og 3 minutter på den anden side. Hvis baconen stadig er lys og blød, steg 1 minut ekstra på hver side. Tag baconen af panden, og behold baconfedtet på panden."
  } ]
}'::jsonb,1,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('8b3964be-dd57-3d6a-afa0-e79035cb8097','d64a0705-7720-37d2-8936-131a1dc6444b','[structured instruction]','{
  "parts" : [ {
    "text" : "Skru panden ned på 4/9. Tilsæt "
  }, {
    "recipeIngredientId" : "24ee8cfb-f81d-3b59-bcb4-52f17f75f5c6"
  }, {
    "text" : " og lad det smelte i cirka 30 sekunder. Vip panden, så fedtstoffet fordeles over bunden."
  } ]
}'::jsonb,2,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('8bf02fed-3d0e-338e-a405-f408b2eb45e0','d64a0705-7720-37d2-8936-131a1dc6444b','[structured instruction]','{
  "parts" : [ {
    "text" : "Giv æggeblandingen en kort omrøring og hæld den på panden. Lad den stå urørt i 1 minut."
  } ]
}'::jsonb,3,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('2bf32bd1-779c-315f-9c9b-c92dc0caec74','d64a0705-7720-37d2-8936-131a1dc6444b','[structured instruction]','{
  "parts" : [ {
    "text" : "Træk forsigtigt den stivnede æggemasse fra kanten ind mod midten, og vip panden, så flydende æg løber ud i de tomme områder. Gentag cirka hvert 30. sekund i 2 minutter. Stop derefter med at røre."
  } ]
}'::jsonb,4,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('8f00b3db-8720-34f5-9a4e-687d93f7fb3e','d64a0705-7720-37d2-8936-131a1dc6444b','[structured instruction]','{
  "parts" : [ {
    "text" : "Læg den stegte bacon oven på æggekagen. Læg låg på panden, behold varmen på 4/9, og steg uden at løfte låget i 3 minutter."
  } ]
}'::jsonb,5,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('bcd26502-015d-31d7-9191-e992cb278428','d64a0705-7720-37d2-8936-131a1dc6444b','[structured instruction]','{
  "parts" : [ {
    "text" : "Sluk for varmen, og lad æggekagen stå på eftervarmen med låget på i 2 minutter. Tag låget af. Æggekagen er færdig, når der ikke længere er synligt flydende æg på toppen. Den skal stadig være blød og fluffy."
  } ]
}'::jsonb,6,'TEXT',NULL);
COMMIT;
