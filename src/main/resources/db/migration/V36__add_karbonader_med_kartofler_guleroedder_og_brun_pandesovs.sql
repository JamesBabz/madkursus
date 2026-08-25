-- REVIEW CANDIDATE: rename V_NEXT before deployment. Generated deterministically.
-- Existing copied Recipes are intentionally untouched.
BEGIN;
DELETE FROM recipe_template_process_bindings WHERE recipe_template_step_id IN (SELECT id FROM recipe_template_steps WHERE recipe_template_id='bc01b52c-f60e-361b-9f13-ed11243413b6');
DELETE FROM recipe_template_prepared_component_ingredients WHERE prepared_component_id IN (SELECT id FROM recipe_template_prepared_components WHERE recipe_template_id='bc01b52c-f60e-361b-9f13-ed11243413b6');
DELETE FROM recipe_template_preparation_steps WHERE recipe_template_id='bc01b52c-f60e-361b-9f13-ed11243413b6';
DELETE FROM recipe_template_steps WHERE recipe_template_id='bc01b52c-f60e-361b-9f13-ed11243413b6';
DELETE FROM recipe_template_prepared_components WHERE recipe_template_id='bc01b52c-f60e-361b-9f13-ed11243413b6';
DELETE FROM recipe_template_ingredients WHERE recipe_template_id='bc01b52c-f60e-361b-9f13-ed11243413b6';
INSERT INTO recipe_templates(id,name,normalized_name,description,active,created_at,updated_at) VALUES ('bc01b52c-f60e-361b-9f13-ed11243413b6','Karbonader med kartofler, gulerødder og brun pandesovs','karbonader med kartofler, gulerødder og brun pandesovs',NULL,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON CONFLICT(id) DO UPDATE SET name=EXCLUDED.name,normalized_name=EXCLUDED.normalized_name,description=EXCLUDED.description,active=true,updated_at=CURRENT_TIMESTAMP;
DELETE FROM recipe_template_equipment_requirements WHERE recipe_template_id='bc01b52c-f60e-361b-9f13-ed11243413b6';
INSERT INTO recipe_template_equipment_requirements(id,recipe_template_id,equipment_type,label,sort_order) VALUES ('8fffb47f-16df-3129-8205-14beb6e8412c','bc01b52c-f60e-361b-9f13-ed11243413b6','STOVE',NULL,1);
INSERT INTO recipe_template_equipment_requirements(id,recipe_template_id,equipment_type,label,sort_order) VALUES ('639de4c0-8b0b-3c66-b688-eb4cb3eadc88','bc01b52c-f60e-361b-9f13-ed11243413b6','POT',NULL,2);
INSERT INTO recipe_template_equipment_requirements(id,recipe_template_id,equipment_type,label,sort_order) VALUES ('18ff4547-2f05-3758-978f-5cd3a6ffcec4','bc01b52c-f60e-361b-9f13-ed11243413b6','PAN',NULL,3);
INSERT INTO recipe_template_equipment_requirements(id,recipe_template_id,equipment_type,label,sort_order) VALUES ('8fd1e310-67b9-33b3-be92-cc982df0aca7','bc01b52c-f60e-361b-9f13-ed11243413b6','THERMOMETER',NULL,4);
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '6b729c25-d1ad-34cb-ab2d-60b3f110dc7a','bc01b52c-f60e-361b-9f13-ed11243413b6',id,200,'GRAM',NULL,1 FROM product_templates WHERE id='e0b5b5a1-653e-30aa-b35f-48930357a493';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '60f5d28e-360e-363a-b1f9-aec6c0f69ef6','bc01b52c-f60e-361b-9f13-ed11243413b6',id,0.5,'PIECE',NULL,2 FROM product_templates WHERE id='971312c9-0d64-3d48-877a-e8c17977e523';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '6315696d-8491-3637-b57e-8bf92abae0ca','bc01b52c-f60e-361b-9f13-ed11243413b6',id,1,'PIECE',NULL,3 FROM product_templates WHERE id='60e27233-16b9-395f-8aac-ad23cc1209a4';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'de8a7ef1-28ae-31cf-af06-17e9a1063825','bc01b52c-f60e-361b-9f13-ed11243413b6',id,1.25,'TABLESPOON',NULL,4 FROM product_templates WHERE id='79d3cfe2-9723-3844-b21f-f7b543d13aa1';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'af7cbfb5-ac20-32e0-8281-a66d4839e4a8','bc01b52c-f60e-361b-9f13-ed11243413b6',id,168.75,'MILLILITER',NULL,5 FROM product_templates WHERE id='bfe6512e-55cb-39bd-a8c8-89f6a068285d';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'fa947ff2-eece-357d-9b9a-040f33d2a1ad','bc01b52c-f60e-361b-9f13-ed11243413b6',id,0.5,'TEASPOON',NULL,6 FROM product_templates WHERE id='f960beee-dc58-3e81-b4e2-da7f1feb354e';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'dc792964-d594-36a9-9938-46f3d2f6975f','bc01b52c-f60e-361b-9f13-ed11243413b6',id,6.5,'GRINDER_TURN',NULL,7 FROM product_templates WHERE id='2c65e27d-5cac-386d-a8e2-b56ccf62205f';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '75ee3adf-0f23-32ff-9a31-3cbafad4e4d9','bc01b52c-f60e-361b-9f13-ed11243413b6',id,30,'GRAM',NULL,8 FROM product_templates WHERE id='74a50c49-20cb-3f89-a61b-c9a02ce9f5e5';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '4657ae2c-4637-3140-8a99-726e9781f770','bc01b52c-f60e-361b-9f13-ed11243413b6',id,12.5,'GRAM',NULL,9 FROM product_templates WHERE id='40867ff0-8b5e-3e4b-a4c2-67596c15aef6';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '53200497-1c2d-313d-a479-3d22c7d1618b','bc01b52c-f60e-361b-9f13-ed11243413b6',id,250,'GRAM',NULL,10 FROM product_templates WHERE id='4d130604-de3d-3289-ae2f-0391815e028b';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '3e75a656-e381-3198-80c7-54769d08064e','bc01b52c-f60e-361b-9f13-ed11243413b6',id,125,'GRAM',NULL,11 FROM product_templates WHERE id='735c8e27-2644-308b-96ed-5db2e850e080';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'b02eab85-36aa-3e24-b62d-2fa19cb69717','bc01b52c-f60e-361b-9f13-ed11243413b6',id,0.125,'PIECE',NULL,12 FROM product_templates WHERE id='971b26fb-4080-3eee-89c5-210bc575383b';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '1b9becc1-d052-3681-b11b-203e8879f103','bc01b52c-f60e-361b-9f13-ed11243413b6',id,0.25,'TEASPOON',NULL,13 FROM product_templates WHERE id='2e84719d-bbff-37aa-8e71-0a7f7f4be6e5';
INSERT INTO recipe_template_prepared_components(id,recipe_template_id,component_key,name,sort_order) VALUES ('9872b969-5ca2-330e-8377-1ebfc07b5640','bc01b52c-f60e-361b-9f13-ed11243413b6','FARS','Fars til karbonader',1);
INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('415bec31-a63d-3f73-b6d6-649edcf34a4e','9872b969-5ca2-330e-8377-1ebfc07b5640','6b729c25-d1ad-34cb-ab2d-60b3f110dc7a',200,'GRAM',1);
INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('9b56c201-d0ae-30ef-beba-fa7ca21b54b8','9872b969-5ca2-330e-8377-1ebfc07b5640','60f5d28e-360e-363a-b1f9-aec6c0f69ef6',0.25,'PIECE',2);
INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('fa20360d-a2b4-30a0-873c-98b8c7f0d16d','9872b969-5ca2-330e-8377-1ebfc07b5640','6315696d-8491-3637-b57e-8bf92abae0ca',0.5,'PIECE',3);
INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('f532ee16-24f3-3b3a-a62d-89f1e2cbf386','9872b969-5ca2-330e-8377-1ebfc07b5640','de8a7ef1-28ae-31cf-af06-17e9a1063825',0.75,'TABLESPOON',4);
INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('996e1c04-9a8b-3532-bc18-bcd401fbe966','9872b969-5ca2-330e-8377-1ebfc07b5640','af7cbfb5-ac20-32e0-8281-a66d4839e4a8',18.75,'MILLILITER',5);
INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('dca8c127-3f13-3501-bba1-3324d0ee01fd','9872b969-5ca2-330e-8377-1ebfc07b5640','fa947ff2-eece-357d-9b9a-040f33d2a1ad',0.375,'TEASPOON',6);
INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('80057ef3-cc62-34a6-8242-77b424f5970e','9872b969-5ca2-330e-8377-1ebfc07b5640','dc792964-d594-36a9-9938-46f3d2f6975f',4,'GRINDER_TURN',7);
INSERT INTO recipe_template_prepared_components(id,recipe_template_id,component_key,name,sort_order) VALUES ('420fe4b4-0665-3432-8831-8aaae16f21b1','bc01b52c-f60e-361b-9f13-ed11243413b6','SOVSEINGREDIENSER','Ingredienser til brun pandesovs',2);
INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('300ce34b-e758-314d-9e65-84aa89680757','420fe4b4-0665-3432-8831-8aaae16f21b1','60f5d28e-360e-363a-b1f9-aec6c0f69ef6',0.25,'PIECE',1);
INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('21145782-5269-3674-a841-6ba865ae5548','420fe4b4-0665-3432-8831-8aaae16f21b1','4657ae2c-4637-3140-8a99-726e9781f770',5,'GRAM',2);
INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('bdbdb457-b5e7-3b2d-946d-cbd8879cca9e','420fe4b4-0665-3432-8831-8aaae16f21b1','de8a7ef1-28ae-31cf-af06-17e9a1063825',0.5,'TABLESPOON',3);
INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('72852874-b944-3b5d-a2e4-5ac7d6f7decf','420fe4b4-0665-3432-8831-8aaae16f21b1','af7cbfb5-ac20-32e0-8281-a66d4839e4a8',150,'MILLILITER',4);
INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('2589b4b2-afd8-35f5-ace9-9c6873dc2ddb','420fe4b4-0665-3432-8831-8aaae16f21b1','b02eab85-36aa-3e24-b62d-2fa19cb69717',0.125,'PIECE',5);
INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('0e2915cb-058f-3e92-a54e-3208d3c5b4e1','420fe4b4-0665-3432-8831-8aaae16f21b1','1b9becc1-d052-3681-b11b-203e8879f103',0.25,'TEASPOON',6);
INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('f4b54cdc-139a-3208-b7c4-322004f8c6fc','420fe4b4-0665-3432-8831-8aaae16f21b1','dc792964-d594-36a9-9938-46f3d2f6975f',2.5,'GRINDER_TURN',7);
INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('3920a194-bbda-32a2-ae74-aa117d5cbcbb','420fe4b4-0665-3432-8831-8aaae16f21b1','fa947ff2-eece-357d-9b9a-040f33d2a1ad',0.125,'TEASPOON',8);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('2ce5b756-9fec-3006-9d63-9f9939c4e0eb','bc01b52c-f60e-361b-9f13-ed11243413b6',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Hak "
  }, {
    "recipeIngredientId" : "60f5d28e-360e-363a-b1f9-aec6c0f69ef6",
    "quantity" : 0.5,
    "unit" : "PIECE"
  }, {
    "text" : " fint. Del det i to lige store portioner: én til farsen og én til sovsen."
  } ]
}'::jsonb,1);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('a153cdcb-9265-3c60-bd8c-9cb4044ef6b7','bc01b52c-f60e-361b-9f13-ed11243413b6',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Skræl "
  }, {
    "recipeIngredientId" : "53200497-1c2d-313d-a479-3d22c7d1618b",
    "quantity" : 250,
    "unit" : "GRAM"
  }, {
    "text" : " og skær dem i nogenlunde ens stykker."
  } ]
}'::jsonb,2);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('7f53669c-5e0e-3864-954f-4e3f190e6f7e','bc01b52c-f60e-361b-9f13-ed11243413b6',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Skræl "
  }, {
    "recipeIngredientId" : "3e75a656-e381-3198-80c7-54769d08064e",
    "quantity" : 125,
    "unit" : "GRAM"
  }, {
    "text" : " og skær dem i skiver på cirka 1 cm."
  } ]
}'::jsonb,3);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('2053c99b-13cf-32dc-b529-b3e63aa03115','bc01b52c-f60e-361b-9f13-ed11243413b6','9872b969-5ca2-330e-8377-1ebfc07b5640','[structured instruction]','{
  "parts" : [ {
    "text" : "Kom "
  }, {
    "recipeIngredientId" : "6b729c25-d1ad-34cb-ab2d-60b3f110dc7a",
    "quantity" : 200,
    "unit" : "GRAM"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "60f5d28e-360e-363a-b1f9-aec6c0f69ef6",
    "quantity" : 0.25,
    "unit" : "PIECE"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "6315696d-8491-3637-b57e-8bf92abae0ca",
    "quantity" : 0.5,
    "unit" : "PIECE"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "de8a7ef1-28ae-31cf-af06-17e9a1063825",
    "quantity" : 0.75,
    "unit" : "TABLESPOON"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "af7cbfb5-ac20-32e0-8281-a66d4839e4a8",
    "quantity" : 1.25,
    "unit" : "TABLESPOON"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "fa947ff2-eece-357d-9b9a-040f33d2a1ad",
    "quantity" : 0.375,
    "unit" : "TEASPOON"
  }, {
    "text" : " og "
  }, {
    "recipeIngredientId" : "dc792964-d594-36a9-9938-46f3d2f6975f",
    "quantity" : 4,
    "unit" : "GRINDER_TURN"
  }, {
    "text" : " i en skål."
  } ]
}'::jsonb,4);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('74a74319-5bc6-3031-976d-90176126c6ec','bc01b52c-f60e-361b-9f13-ed11243413b6',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Pisk "
  }, {
    "recipeIngredientId" : "6315696d-8491-3637-b57e-8bf92abae0ca",
    "quantity" : 0.5,
    "unit" : "PIECE"
  }, {
    "text" : " sammen på en tallerken. Kom "
  }, {
    "recipeIngredientId" : "75ee3adf-0f23-32ff-9a31-3cbafad4e4d9",
    "quantity" : 30,
    "unit" : "GRAM"
  }, {
    "text" : " på en anden tallerken."
  } ]
}'::jsonb,5);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('6f3cf140-4880-3eaa-8b14-84a75a4c03d5','bc01b52c-f60e-361b-9f13-ed11243413b6',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Mål "
  }, {
    "recipeIngredientId" : "4657ae2c-4637-3140-8a99-726e9781f770",
    "quantity" : 7.5,
    "unit" : "GRAM"
  }, {
    "text" : " af til stegning af karbonaderne."
  } ]
}'::jsonb,6);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('3fa4aeb2-7a75-342c-ade1-2287b4953c11','bc01b52c-f60e-361b-9f13-ed11243413b6','420fe4b4-0665-3432-8831-8aaae16f21b1','[structured instruction]','{
  "parts" : [ {
    "text" : "Stil "
  }, {
    "recipeIngredientId" : "60f5d28e-360e-363a-b1f9-aec6c0f69ef6",
    "quantity" : 0.25,
    "unit" : "PIECE"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "4657ae2c-4637-3140-8a99-726e9781f770",
    "quantity" : 5,
    "unit" : "GRAM"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "de8a7ef1-28ae-31cf-af06-17e9a1063825",
    "quantity" : 0.5,
    "unit" : "TABLESPOON"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "af7cbfb5-ac20-32e0-8281-a66d4839e4a8",
    "quantity" : 1.5,
    "unit" : "DECILITER"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "b02eab85-36aa-3e24-b62d-2fa19cb69717",
    "quantity" : 0.125,
    "unit" : "PIECE"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "1b9becc1-d052-3681-b11b-203e8879f103",
    "quantity" : 0.25,
    "unit" : "TEASPOON"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "dc792964-d594-36a9-9938-46f3d2f6975f",
    "quantity" : 2.5,
    "unit" : "GRINDER_TURN"
  }, {
    "text" : " og "
  }, {
    "recipeIngredientId" : "fa947ff2-eece-357d-9b9a-040f33d2a1ad",
    "quantity" : 0.125,
    "unit" : "TEASPOON"
  }, {
    "text" : " klar til sovsen."
  } ]
}'::jsonb,7);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('d98d870b-5fb2-357f-934f-0d07f549fa89','bc01b52c-f60e-361b-9f13-ed11243413b6',NULL,NULL,1,'PROCESS',(SELECT id FROM cooking_processes WHERE process_key='MIX_MEATBALL_MIXTURE'));
INSERT INTO recipe_template_process_bindings(id,recipe_template_step_id,parameter_key,recipe_ingredient_id,product_template_id,prepared_component_id,quantity,unit,duration_seconds,temperature_celsius,heat_level,number_value,text_value) VALUES ('051ac0ac-928a-3e1a-a5f9-2fff3083c308','d98d870b-5fb2-357f-934f-0d07f549fa89','BASE',NULL,NULL,'9872b969-5ca2-330e-8377-1ebfc07b5640',NULL,NULL,NULL,NULL,NULL,NULL,NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('4b5fdb44-e862-3b90-9b84-0cb01d8228b2','bc01b52c-f60e-361b-9f13-ed11243413b6','[structured instruction]','{
  "parts" : [ {
    "text" : "Del farsen og form cirka "
  }, {
    "scaledNumber" : 2
  }, {
    "text" : " karbonader. Tryk dem flade, så de er cirka 2 cm tykke."
  } ]
}'::jsonb,2,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('1160ca8f-5037-3991-b9f2-1edab61b44b5','bc01b52c-f60e-361b-9f13-ed11243413b6','[structured instruction]','{
  "parts" : [ {
    "text" : "Kom "
  }, {
    "recipeIngredientId" : "53200497-1c2d-313d-a479-3d22c7d1618b"
  }, {
    "text" : " og "
  }, {
    "recipeIngredientId" : "3e75a656-e381-3198-80c7-54769d08064e"
  }, {
    "text" : " i samme gryde."
  } ]
}'::jsonb,3,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('de8c15c3-22f6-3497-ab5f-f744a933161c','bc01b52c-f60e-361b-9f13-ed11243413b6',NULL,NULL,4,'PROCESS',(SELECT id FROM cooking_processes WHERE process_key='BOIL_POTATOES'));
INSERT INTO recipe_template_process_bindings(id,recipe_template_step_id,parameter_key,recipe_ingredient_id,product_template_id,prepared_component_id,quantity,unit,duration_seconds,temperature_celsius,heat_level,number_value,text_value) VALUES ('f8599116-e330-3d0a-880d-5fee07cbc753','de8c15c3-22f6-3497-ab5f-f744a933161c','POTATOES','53200497-1c2d-313d-a479-3d22c7d1618b','4d130604-de3d-3289-ae2f-0391815e028b',NULL,250,'GRAM',NULL,NULL,NULL,NULL,NULL);
INSERT INTO recipe_template_process_bindings(id,recipe_template_step_id,parameter_key,recipe_ingredient_id,product_template_id,prepared_component_id,quantity,unit,duration_seconds,temperature_celsius,heat_level,number_value,text_value) VALUES ('f21727a0-e1ff-345d-a061-13a1a94a79f9','de8c15c3-22f6-3497-ab5f-f744a933161c','SIMMER_TIME',NULL,NULL,NULL,NULL,NULL,1200,NULL,NULL,NULL,NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('527bbab4-3eaf-35cc-a125-bb45bfa39203','bc01b52c-f60e-361b-9f13-ed11243413b6','Kontrollér et af de største kartoffelstykker med en kniv. Kartoflerne er færdige, når kniven glider let igennem. Hæld derefter vandet fra og læg låget på gryden.',NULL,5,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('1938549d-ef25-35c6-b083-4f8c7105b050','bc01b52c-f60e-361b-9f13-ed11243413b6','[structured instruction]','{
  "parts" : [ {
    "text" : "Sæt panden på trin 7 og tilsæt "
  }, {
    "recipeIngredientId" : "4657ae2c-4637-3140-8a99-726e9781f770",
    "quantity" : 7.5,
    "unit" : "GRAM"
  }, {
    "text" : ". Vent til smørret er smeltet og bruser. Vend én karbonade ad gangen i "
  }, {
    "recipeIngredientId" : "6315696d-8491-3637-b57e-8bf92abae0ca",
    "quantity" : 0.5,
    "unit" : "PIECE"
  }, {
    "text" : " og derefter i "
  }, {
    "recipeIngredientId" : "75ee3adf-0f23-32ff-9a31-3cbafad4e4d9",
    "quantity" : 30,
    "unit" : "GRAM"
  }, {
    "text" : ". Læg karbonaden direkte på den varme pande efter panering, før den næste paneres."
  } ]
}'::jsonb,6,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('0f4763f3-c8d7-3ff1-92dd-e8a5cecf9fcb','bc01b52c-f60e-361b-9f13-ed11243413b6','Steg karbonaderne i 3 minutter uden at flytte dem. Vend dem og steg den anden side i 3 minutter. Skru derefter ned på trin 5. Steg videre i 4 minutter og vend dem efter 2 minutter.',NULL,7,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('c8c30077-29c1-3824-9c6f-909cb7c3bb2c','bc01b52c-f60e-361b-9f13-ed11243413b6','Mål kernetemperaturen i den tykkeste karbonade ved at stikke stegetermometeret ind fra siden mod midten. Fortsæt stegningen om nødvendigt, til midten er mindst 70 °C. Tag derefter karbonaderne af panden.',NULL,8,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('342d7214-5afc-37ec-8ebe-40e84bb54c5d','bc01b52c-f60e-361b-9f13-ed11243413b6','Hvis kartoflerne endnu ikke er færdige, sluk panden og flyt den væk fra det varme blus, så stegeresterne ikke brænder.',NULL,9,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('57e5c264-2c38-3c0f-b078-5125efdb44e6','bc01b52c-f60e-361b-9f13-ed11243413b6','[structured instruction]','{
  "parts" : [ {
    "text" : "Brug den samme pande til sovsen. Fjern kun eventuelle helt sorte løse rester. Sæt panden på trin 4. Tilsæt "
  }, {
    "recipeIngredientId" : "4657ae2c-4637-3140-8a99-726e9781f770",
    "quantity" : 5,
    "unit" : "GRAM"
  }, {
    "text" : " og "
  }, {
    "recipeIngredientId" : "60f5d28e-360e-363a-b1f9-aec6c0f69ef6",
    "quantity" : 0.25,
    "unit" : "PIECE"
  }, {
    "text" : ". Steg løget i 2 minutter og rør cirka hvert 30. sekund."
  } ]
}'::jsonb,10,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('ffbf5eba-2b98-34bf-9bc1-c168d6c4a756','bc01b52c-f60e-361b-9f13-ed11243413b6','[structured instruction]','{
  "parts" : [ {
    "text" : "Tilsæt "
  }, {
    "recipeIngredientId" : "de8a7ef1-28ae-31cf-af06-17e9a1063825",
    "quantity" : 0.5,
    "unit" : "TABLESPOON"
  }, {
    "text" : " og rør mel, løg, smør og stegerester grundigt sammen i 1 minut."
  } ]
}'::jsonb,11,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('1adeac4d-03bd-3408-a83e-060d0334e2c7','bc01b52c-f60e-361b-9f13-ed11243413b6','[structured instruction]','{
  "parts" : [ {
    "text" : "Tilsæt "
  }, {
    "recipeIngredientId" : "af7cbfb5-ac20-32e0-8281-a66d4839e4a8",
    "quantity" : 1.5,
    "unit" : "DECILITER"
  }, {
    "text" : " i 3 omtrent lige store portioner. Pisk sovsen helt jævn mellem hver tilsætning."
  } ]
}'::jsonb,12,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('b26ca642-e30b-3f55-9682-f6527723b9b7','bc01b52c-f60e-361b-9f13-ed11243413b6','[structured instruction]','{
  "parts" : [ {
    "text" : "Smuldr "
  }, {
    "recipeIngredientId" : "b02eab85-36aa-3e24-b62d-2fa19cb69717",
    "quantity" : 0.125,
    "unit" : "PIECE"
  }, {
    "text" : " i sovsen og pisk, til den er opløst. Lad sovsen varme op på trin 4. Når den bobler forsigtigt, lad den boble i 2 minutter og pisk cirka hvert 20.-30. sekund."
  } ]
}'::jsonb,13,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('5a098674-be9c-30b6-8fc3-e425e5d9ae81','bc01b52c-f60e-361b-9f13-ed11243413b6','[structured instruction]','{
  "parts" : [ {
    "text" : "Tilsæt "
  }, {
    "recipeIngredientId" : "1b9becc1-d052-3681-b11b-203e8879f103",
    "quantity" : 0.25,
    "unit" : "TEASPOON"
  }, {
    "text" : " og "
  }, {
    "recipeIngredientId" : "dc792964-d594-36a9-9938-46f3d2f6975f",
    "quantity" : 2.5,
    "unit" : "GRINDER_TURN"
  }, {
    "text" : ". Pisk det ud i sovsen. Tilsæt derefter "
  }, {
    "recipeIngredientId" : "fa947ff2-eece-357d-9b9a-040f33d2a1ad",
    "quantity" : 0.125,
    "unit" : "TEASPOON"
  }, {
    "text" : " og pisk igen."
  } ]
}'::jsonb,14,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('eb966388-37a0-38f0-88ac-a646e7d4e437','bc01b52c-f60e-361b-9f13-ed11243413b6','Sluk panden og servér karbonaderne med kartofler, gulerødder og brun pandesovs.',NULL,15,'TEXT',NULL);
COMMIT;
