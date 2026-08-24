-- REVIEW CANDIDATE: rename V_NEXT before deployment. Generated deterministically.
-- Existing copied Recipes are intentionally untouched.
BEGIN;
DELETE FROM recipe_template_process_bindings WHERE recipe_template_step_id IN (SELECT id FROM recipe_template_steps WHERE recipe_template_id='f94ea16d-7040-3bbc-9432-3455cc0c9360');
DELETE FROM recipe_template_prepared_component_ingredients WHERE prepared_component_id IN (SELECT id FROM recipe_template_prepared_components WHERE recipe_template_id='f94ea16d-7040-3bbc-9432-3455cc0c9360');
DELETE FROM recipe_template_preparation_steps WHERE recipe_template_id='f94ea16d-7040-3bbc-9432-3455cc0c9360';
DELETE FROM recipe_template_steps WHERE recipe_template_id='f94ea16d-7040-3bbc-9432-3455cc0c9360';
DELETE FROM recipe_template_prepared_components WHERE recipe_template_id='f94ea16d-7040-3bbc-9432-3455cc0c9360';
DELETE FROM recipe_template_ingredients WHERE recipe_template_id='f94ea16d-7040-3bbc-9432-3455cc0c9360';
INSERT INTO recipe_templates(id,name,normalized_name,description,active,created_at,updated_at) VALUES ('f94ea16d-7040-3bbc-9432-3455cc0c9360','Kødboller i tomatsovs med pasta','kødboller i tomatsovs med pasta','Stegte kødboller, der færdiggøres i tomatsovs og serveres med penne.',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON CONFLICT(id) DO UPDATE SET name=EXCLUDED.name,normalized_name=EXCLUDED.normalized_name,description=EXCLUDED.description,active=true,updated_at=CURRENT_TIMESTAMP;
DELETE FROM recipe_template_equipment_requirements WHERE recipe_template_id='f94ea16d-7040-3bbc-9432-3455cc0c9360';
INSERT INTO recipe_template_equipment_requirements(id,recipe_template_id,equipment_type,label,sort_order) VALUES ('3dda446f-f0c4-35a8-b84d-2578f3dfbd87','f94ea16d-7040-3bbc-9432-3455cc0c9360','STOVE',NULL,1);
INSERT INTO recipe_template_equipment_requirements(id,recipe_template_id,equipment_type,label,sort_order) VALUES ('db75d150-f571-37a9-a6b4-b1c39069f7e2','f94ea16d-7040-3bbc-9432-3455cc0c9360','PAN',NULL,2);
INSERT INTO recipe_template_equipment_requirements(id,recipe_template_id,equipment_type,label,sort_order) VALUES ('2dae7415-558a-3177-ab6d-953fcaa5ecf0','f94ea16d-7040-3bbc-9432-3455cc0c9360','POT',NULL,3);
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'c17c69e3-c704-3535-82e4-c1abd8348d8e','f94ea16d-7040-3bbc-9432-3455cc0c9360',id,200,'GRAM',NULL,1 FROM product_templates WHERE id='4109bef8-9933-357a-9563-e7352a72f3f2';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '7ff0fe6a-19a2-3912-8567-8a6d93c2b4b2','f94ea16d-7040-3bbc-9432-3455cc0c9360',id,0.5,'PIECE',NULL,2 FROM product_templates WHERE id='971312c9-0d64-3d48-877a-e8c17977e523';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '022b58cc-3f23-30b8-a4fc-c45db8dca981','f94ea16d-7040-3bbc-9432-3455cc0c9360',id,0.5,'PIECE',NULL,3 FROM product_templates WHERE id='60e27233-16b9-395f-8aac-ad23cc1209a4';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'fe559659-4857-3a94-a145-e6ffccae48e1','f94ea16d-7040-3bbc-9432-3455cc0c9360',id,1,'TABLESPOON',NULL,4 FROM product_templates WHERE id='79d3cfe2-9723-3844-b21f-f7b543d13aa1';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'c8172e6b-0431-3417-841c-f5b9b8bca707','f94ea16d-7040-3bbc-9432-3455cc0c9360',id,0.25,'DECILITER',NULL,5 FROM product_templates WHERE id='bfe6512e-55cb-39bd-a8c8-89f6a068285d';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'a0eb8bf5-54f7-323f-be23-e1f00bd688e8','f94ea16d-7040-3bbc-9432-3455cc0c9360',id,0.75,'TEASPOON',NULL,6 FROM product_templates WHERE id='f960beee-dc58-3e81-b4e2-da7f1feb354e';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'b7f4ace7-8057-322b-ae89-52b2fec397ac','f94ea16d-7040-3bbc-9432-3455cc0c9360',id,7.5,'GRINDER_TURN',NULL,7 FROM product_templates WHERE id='2c65e27d-5cac-386d-a8e2-b56ccf62205f';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'c4784d6e-be91-3a9a-8d51-a7c44006b24a','f94ea16d-7040-3bbc-9432-3455cc0c9360',id,0.25,'TEASPOON',NULL,8 FROM product_templates WHERE id='4fd35177-2edc-3f9f-8a52-0e59525a91aa';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '18943e10-2294-31d5-8612-e64d1f7864f5','f94ea16d-7040-3bbc-9432-3455cc0c9360',id,0.5,'TABLESPOON',NULL,9 FROM product_templates WHERE id='4b63577c-a7ef-327a-acef-6ef6010b7d6a';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '1144dac8-e0fa-35be-a591-f510998542e4','f94ea16d-7040-3bbc-9432-3455cc0c9360',id,1,'PIECE',NULL,10 FROM product_templates WHERE id='62ca13f3-2d1c-3419-a3c3-92535c728291';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '8c27df09-a8d7-3bdd-b80f-a36b5b69f127','f94ea16d-7040-3bbc-9432-3455cc0c9360',id,0.5,'TABLESPOON',NULL,11 FROM product_templates WHERE id='e571cf3b-8a8e-3e7b-a491-bda2cd6585bd';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'eef60106-ce8c-3b1b-a3d5-e21aa2dd0f3e','f94ea16d-7040-3bbc-9432-3455cc0c9360',id,200,'GRAM',NULL,12 FROM product_templates WHERE id='3288d148-be29-3a16-a67c-210665011c47';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'ea348f8f-a82c-3e7e-aa2f-46c8f0243891','f94ea16d-7040-3bbc-9432-3455cc0c9360',id,0.5,'DECILITER',NULL,13 FROM product_templates WHERE id='04a53a53-364c-373c-8fe9-68e4146652d4';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '68e8b6fd-d6c9-302e-a83d-b458fdfe8217','f94ea16d-7040-3bbc-9432-3455cc0c9360',id,0.25,'TEASPOON',NULL,14 FROM product_templates WHERE id='9ed863cd-3688-3e8b-95cd-1c8cc4b3c4f7';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'd1098f75-2952-350a-966e-0053e5fa84fd','f94ea16d-7040-3bbc-9432-3455cc0c9360',id,100,'GRAM',NULL,15 FROM product_templates WHERE id='99f6dde2-0756-3f15-b72c-5fa5fe69d936';
INSERT INTO recipe_template_prepared_components(id,recipe_template_id,component_key,name,sort_order) VALUES ('8f5a0824-2176-3fd2-ae63-23a0578fd1d8','f94ea16d-7040-3bbc-9432-3455cc0c9360','FARS_INGREDIENSER','Ingredienser til fars',1);
INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('563880db-df91-39f1-ae9d-f352bba4379b','8f5a0824-2176-3fd2-ae63-23a0578fd1d8','c17c69e3-c704-3535-82e4-c1abd8348d8e',200,'GRAM',1);
INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('3c2d8bc9-bc59-306a-adb8-87062d69b9e9','8f5a0824-2176-3fd2-ae63-23a0578fd1d8','7ff0fe6a-19a2-3912-8567-8a6d93c2b4b2',0.25,'PIECE',2);
INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('8cf89f6e-7577-38da-8630-953bfa121368','8f5a0824-2176-3fd2-ae63-23a0578fd1d8','022b58cc-3f23-30b8-a4fc-c45db8dca981',0.5,'PIECE',3);
INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('8e1499fb-f609-3134-9b75-eb8eecf2abb3','8f5a0824-2176-3fd2-ae63-23a0578fd1d8','fe559659-4857-3a94-a145-e6ffccae48e1',1,'TABLESPOON',4);
INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('c84798f1-3131-3135-bbcc-99ce3d18e4a3','8f5a0824-2176-3fd2-ae63-23a0578fd1d8','c8172e6b-0431-3417-841c-f5b9b8bca707',0.25,'DECILITER',5);
INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('20ae56a8-9512-320c-97b8-c685f06c0e5a','8f5a0824-2176-3fd2-ae63-23a0578fd1d8','a0eb8bf5-54f7-323f-be23-e1f00bd688e8',0.5,'TEASPOON',6);
INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('e0c39c3f-c70d-366c-81be-a68c6f0ff632','8f5a0824-2176-3fd2-ae63-23a0578fd1d8','b7f4ace7-8057-322b-ae89-52b2fec397ac',5,'GRINDER_TURN',7);
INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('4c4f7ce3-6738-3921-ad0d-a7a1873cbf99','8f5a0824-2176-3fd2-ae63-23a0578fd1d8','c4784d6e-be91-3a9a-8d51-a7c44006b24a',0.25,'TEASPOON',8);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('1ba2c6e8-7c2d-3fcb-ace0-6104674970b7','f94ea16d-7040-3bbc-9432-3455cc0c9360',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Hak "
  }, {
    "recipeIngredientId" : "7ff0fe6a-19a2-3912-8567-8a6d93c2b4b2",
    "quantity" : 0.5,
    "unit" : "PIECE"
  }, {
    "text" : " fint. Fordel løget i to lige store dele: den ene til farsen og den anden til tomatsovsen."
  } ]
}'::jsonb,1);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('c60e9682-2224-3de0-88ce-d08fb739640f','f94ea16d-7040-3bbc-9432-3455cc0c9360',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Hak "
  }, {
    "recipeIngredientId" : "1144dac8-e0fa-35be-a591-f510998542e4",
    "quantity" : 1,
    "unit" : "PIECE"
  }, {
    "text" : " fint."
  } ]
}'::jsonb,2);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('2dbe8c48-a35a-363b-983c-1829ef42db56','f94ea16d-7040-3bbc-9432-3455cc0c9360','8f5a0824-2176-3fd2-ae63-23a0578fd1d8','[structured instruction]','{
  "parts" : [ {
    "text" : "Mål "
  }, {
    "recipeIngredientId" : "c17c69e3-c704-3535-82e4-c1abd8348d8e",
    "quantity" : 200,
    "unit" : "GRAM"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "022b58cc-3f23-30b8-a4fc-c45db8dca981",
    "quantity" : 0.5,
    "unit" : "PIECE"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "fe559659-4857-3a94-a145-e6ffccae48e1",
    "quantity" : 1,
    "unit" : "TABLESPOON"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "c8172e6b-0431-3417-841c-f5b9b8bca707",
    "quantity" : 0.25,
    "unit" : "DECILITER"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "a0eb8bf5-54f7-323f-be23-e1f00bd688e8",
    "quantity" : 0.5,
    "unit" : "TEASPOON"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "b7f4ace7-8057-322b-ae89-52b2fec397ac",
    "quantity" : 5,
    "unit" : "GRINDER_TURN"
  }, {
    "text" : " og "
  }, {
    "recipeIngredientId" : "c4784d6e-be91-3a9a-8d51-a7c44006b24a",
    "quantity" : 0.25,
    "unit" : "TEASPOON"
  }, {
    "text" : " op til farsen."
  } ]
}'::jsonb,3);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('8017b0b6-c3f8-35ed-b703-c48c368da10b','f94ea16d-7040-3bbc-9432-3455cc0c9360',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Mål "
  }, {
    "recipeIngredientId" : "8c27df09-a8d7-3bdd-b80f-a36b5b69f127",
    "quantity" : 0.5,
    "unit" : "TABLESPOON"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "eef60106-ce8c-3b1b-a3d5-e21aa2dd0f3e",
    "quantity" : 200,
    "unit" : "GRAM"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "ea348f8f-a82c-3e7e-aa2f-46c8f0243891",
    "quantity" : 0.5,
    "unit" : "DECILITER"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "68e8b6fd-d6c9-302e-a83d-b458fdfe8217",
    "quantity" : 0.25,
    "unit" : "TEASPOON"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "a0eb8bf5-54f7-323f-be23-e1f00bd688e8",
    "quantity" : 0.25,
    "unit" : "TEASPOON"
  }, {
    "text" : " og "
  }, {
    "recipeIngredientId" : "b7f4ace7-8057-322b-ae89-52b2fec397ac",
    "quantity" : 2.5,
    "unit" : "GRINDER_TURN"
  }, {
    "text" : " op til tomatsovsen."
  } ]
}'::jsonb,4);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('1d5f5793-094d-307f-9feb-ecaf9330af48','f94ea16d-7040-3bbc-9432-3455cc0c9360',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Mål "
  }, {
    "recipeIngredientId" : "d1098f75-2952-350a-966e-0053e5fa84fd",
    "quantity" : 100,
    "unit" : "GRAM"
  }, {
    "text" : " op."
  } ]
}'::jsonb,5);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('28049f79-5ea9-3f21-8b40-c4e349fbf720','f94ea16d-7040-3bbc-9432-3455cc0c9360',NULL,NULL,1,'PROCESS',(SELECT id FROM cooking_processes WHERE process_key='MIX_MEATBALL_MIXTURE'));
INSERT INTO recipe_template_process_bindings(id,recipe_template_step_id,parameter_key,recipe_ingredient_id,product_template_id,prepared_component_id,quantity,unit,duration_seconds,temperature_celsius,heat_level,number_value,text_value) VALUES ('2d74be3c-e3a5-32d9-b33f-4327b6bcaad8','28049f79-5ea9-3f21-8b40-c4e349fbf720','BASE',NULL,NULL,'8f5a0824-2176-3fd2-ae63-23a0578fd1d8',NULL,NULL,NULL,NULL,NULL,NULL,NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('cace7c73-982b-3c75-ab8c-cb147b0fd720','f94ea16d-7040-3bbc-9432-3455cc0c9360','[structured instruction]','{
  "parts" : [ {
    "text" : "Form farsen til ca. "
  }, {
    "scaledNumber" : 6
  }, {
    "text" : " kødboller på størrelse med bordtennisbolde."
  } ]
}'::jsonb,2,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('69281b75-223d-32b9-b177-af1e93c39096','f94ea16d-7040-3bbc-9432-3455cc0c9360',NULL,NULL,3,'PROCESS',(SELECT id FROM cooking_processes WHERE process_key='PAN_FRY_MEATBALLS'));
INSERT INTO recipe_template_process_bindings(id,recipe_template_step_id,parameter_key,recipe_ingredient_id,product_template_id,prepared_component_id,quantity,unit,duration_seconds,temperature_celsius,heat_level,number_value,text_value) VALUES ('59b04478-d221-398a-97ba-b8425f50d90b','69281b75-223d-32b9-b177-af1e93c39096','MEATBALLS',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'Formede kødboller');
INSERT INTO recipe_template_process_bindings(id,recipe_template_step_id,parameter_key,recipe_ingredient_id,product_template_id,prepared_component_id,quantity,unit,duration_seconds,temperature_celsius,heat_level,number_value,text_value) VALUES ('89ded771-1aca-35ff-8a24-d6225c955c98','69281b75-223d-32b9-b177-af1e93c39096','FAT','18943e10-2294-31d5-8612-e64d1f7864f5','4b63577c-a7ef-327a-acef-6ef6010b7d6a',NULL,0.5,'TABLESPOON',NULL,NULL,NULL,NULL,NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('5c12b001-f7f0-3120-9fd4-a4c5172644ff','f94ea16d-7040-3bbc-9432-3455cc0c9360','Tag kødbollerne af panden. De behøver ikke være gennemstegte endnu; de færdiggøres i tomatsovsen.',NULL,4,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('343a821e-0559-3e73-a0f7-efe9ecf881f8','f94ea16d-7040-3bbc-9432-3455cc0c9360',NULL,NULL,5,'PROCESS',(SELECT id FROM cooking_processes WHERE process_key='BOIL_PASTA'));
INSERT INTO recipe_template_process_bindings(id,recipe_template_step_id,parameter_key,recipe_ingredient_id,product_template_id,prepared_component_id,quantity,unit,duration_seconds,temperature_celsius,heat_level,number_value,text_value) VALUES ('b96da4e7-d069-3732-8773-b0c3a6fb6d5f','343a821e-0559-3e73-a0f7-efe9ecf881f8','PASTA','d1098f75-2952-350a-966e-0053e5fa84fd','99f6dde2-0756-3f15-b72c-5fa5fe69d936',NULL,100,'GRAM',NULL,NULL,NULL,NULL,NULL);
INSERT INTO recipe_template_process_bindings(id,recipe_template_step_id,parameter_key,recipe_ingredient_id,product_template_id,prepared_component_id,quantity,unit,duration_seconds,temperature_celsius,heat_level,number_value,text_value) VALUES ('0104a3b6-4735-3a70-900b-03de662e5ebc','343a821e-0559-3e73-a0f7-efe9ecf881f8','COOK_TIME',NULL,NULL,NULL,NULL,NULL,720,NULL,NULL,NULL,NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('3c0ec669-8c15-3d37-afc5-d545f8ee0818','f94ea16d-7040-3bbc-9432-3455cc0c9360','[structured instruction]','{
  "parts" : [ {
    "text" : "Skru panden ned til trin 4. Steg den resterende halvdel af det hakkede "
  }, {
    "recipeIngredientId" : "7ff0fe6a-19a2-3912-8567-8a6d93c2b4b2",
    "quantity" : 0.25,
    "unit" : "PIECE"
  }, {
    "text" : " i fedtet fra kødbollerne i 2 minutter, og rør cirka hvert 30. sekund."
  } ]
}'::jsonb,6,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('4bfcc582-4fdc-30b8-815c-b5ce71c2fcfd','f94ea16d-7040-3bbc-9432-3455cc0c9360','[structured instruction]','{
  "parts" : [ {
    "text" : "Tilsæt "
  }, {
    "recipeIngredientId" : "1144dac8-e0fa-35be-a591-f510998542e4",
    "quantity" : 1,
    "unit" : "PIECE"
  }, {
    "text" : " og steg i 30 sekunder. Tilsæt derefter "
  }, {
    "recipeIngredientId" : "8c27df09-a8d7-3bdd-b80f-a36b5b69f127",
    "quantity" : 0.5,
    "unit" : "TABLESPOON"
  }, {
    "text" : " og steg videre i 1 minut, mens du rører."
  } ]
}'::jsonb,7,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('dbd35c74-c875-327f-bff0-4dba919c3377','f94ea16d-7040-3bbc-9432-3455cc0c9360','[structured instruction]','{
  "parts" : [ {
    "text" : "Tilsæt "
  }, {
    "recipeIngredientId" : "eef60106-ce8c-3b1b-a3d5-e21aa2dd0f3e",
    "quantity" : 200,
    "unit" : "GRAM"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "ea348f8f-a82c-3e7e-aa2f-46c8f0243891",
    "quantity" : 0.5,
    "unit" : "DECILITER"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "68e8b6fd-d6c9-302e-a83d-b458fdfe8217",
    "quantity" : 0.25,
    "unit" : "TEASPOON"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "a0eb8bf5-54f7-323f-be23-e1f00bd688e8",
    "quantity" : 0.25,
    "unit" : "TEASPOON"
  }, {
    "text" : " og "
  }, {
    "recipeIngredientId" : "b7f4ace7-8057-322b-ae89-52b2fec397ac",
    "quantity" : 2.5,
    "unit" : "GRINDER_TURN"
  }, {
    "text" : ". Rør grundigt og skrab de brune stegerester fra bunden med op i sovsen. Skru op til trin 6, indtil sovsen bobler tydeligt."
  } ]
}'::jsonb,8,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('8f5b1cf6-e86b-3c1d-ad5f-be91c79502df','f94ea16d-7040-3bbc-9432-3455cc0c9360','Skru ned til trin 3. Læg kødbollerne tilbage i tomatsovsen, vend dem i sovsen og læg låg på. Lad dem småsimre i 10 minutter og vend dem efter 5 minutter.',NULL,9,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('75eebb0a-9d9c-3c5e-b4ca-72d419e7b78b','f94ea16d-7040-3bbc-9432-3455cc0c9360','Hvis kødbollerne er færdige før pastaen, sluk for panden og lad tomatsovsen stå med låg. Smag på en penne før vandet hældes fra; den skal være blød med lidt bid.',NULL,10,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('44fefc78-2f7c-3702-8fa3-41dff84ab108','f94ea16d-7040-3bbc-9432-3455cc0c9360','Server penne med kødboller og tomatsovs ovenpå. Pasta og sovs holdes adskilt frem til servering.',NULL,11,'TEXT',NULL);
COMMIT;
