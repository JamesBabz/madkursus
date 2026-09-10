-- Generated locally. Review and test before deployment.
-- Existing copied Recipes are intentionally untouched.
BEGIN;
INSERT INTO recipe_templates(id,name,normalized_name,description,active,created_at,updated_at) VALUES ('cf89624f-31af-3cf6-8108-32c6ac20e700','Hakkebøffer med kartofler, gulerødder og brun pandesovs','hakkebøffer med kartofler, gulerødder og brun pandesovs','Hakkebøffer med kogte kartofler og gulerødder samt brun pandesovs lavet på stegeresterne fra bøfferne.',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON CONFLICT(id) DO UPDATE SET name=EXCLUDED.name,normalized_name=EXCLUDED.normalized_name,description=EXCLUDED.description,active=true,updated_at=CURRENT_TIMESTAMP;
INSERT INTO recipe_template_equipment_requirements(id,recipe_template_id,equipment_type,label,sort_order) VALUES ('61915377-8195-3261-a5e2-09f1feaf5338','cf89624f-31af-3cf6-8108-32c6ac20e700','STOVE',NULL,1);
INSERT INTO recipe_template_equipment_requirements(id,recipe_template_id,equipment_type,label,sort_order) VALUES ('3db3acf5-0a81-33f2-b807-925a17c7f623','cf89624f-31af-3cf6-8108-32c6ac20e700','POT',NULL,2);
INSERT INTO recipe_template_equipment_requirements(id,recipe_template_id,equipment_type,label,sort_order) VALUES ('0f48acea-1697-3323-a77c-3eff4aa5e8b9','cf89624f-31af-3cf6-8108-32c6ac20e700','PAN',NULL,3);
INSERT INTO recipe_template_equipment_requirements(id,recipe_template_id,equipment_type,label,sort_order) VALUES ('6ae201ef-2165-30f3-86da-6f0156e84251','cf89624f-31af-3cf6-8108-32c6ac20e700','THERMOMETER',NULL,4);
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '45c88ca0-5e6d-3d60-94ab-567060849736','cf89624f-31af-3cf6-8108-32c6ac20e700',id,200,'GRAM',NULL,1 FROM product_templates WHERE id='4109bef8-9933-357a-9563-e7352a72f3f2';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '59671942-0aa7-390f-8cb5-e9920b8df0cc','cf89624f-31af-3cf6-8108-32c6ac20e700',id,250,'GRAM',NULL,2 FROM product_templates WHERE id='4d130604-de3d-3289-ae2f-0391815e028b';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'cb423199-2e44-3ece-b93c-f152afca36a3','cf89624f-31af-3cf6-8108-32c6ac20e700',id,125,'GRAM',NULL,3 FROM product_templates WHERE id='735c8e27-2644-308b-96ed-5db2e850e080';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '57425492-1ade-31ba-841c-704944804078','cf89624f-31af-3cf6-8108-32c6ac20e700',id,0.5,'PIECE',NULL,4 FROM product_templates WHERE id='971312c9-0d64-3d48-877a-e8c17977e523';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'f499fcfe-78a2-3657-a405-2c147aaebaf8','cf89624f-31af-3cf6-8108-32c6ac20e700',id,7.5,'GRAM',NULL,5 FROM product_templates WHERE id='40867ff0-8b5e-3e4b-a4c2-67596c15aef6';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '8b88a056-b806-31ca-94e1-6e7459d8504a','cf89624f-31af-3cf6-8108-32c6ac20e700',id,0.5,'TABLESPOON',NULL,6 FROM product_templates WHERE id='79d3cfe2-9723-3844-b21f-f7b543d13aa1';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'b8f8cd4c-d5d0-3af1-847e-4f328f8ca255','cf89624f-31af-3cf6-8108-32c6ac20e700',id,1.5,'DECILITER',NULL,7 FROM product_templates WHERE id='bfe6512e-55cb-39bd-a8c8-89f6a068285d';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '5e20c32d-9dff-381e-9a61-aa28a7c3d621','cf89624f-31af-3cf6-8108-32c6ac20e700',id,0.125,'PIECE',NULL,8 FROM product_templates WHERE id='971b26fb-4080-3eee-89c5-210bc575383b';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'c2020bc2-7540-3a2b-bdb9-fd19df02b71d','cf89624f-31af-3cf6-8108-32c6ac20e700',id,0.25,'TEASPOON',NULL,9 FROM product_templates WHERE id='2e84719d-bbff-37aa-8e71-0a7f7f4be6e5';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '991469bf-20af-3bb4-9bdc-41555088be03','cf89624f-31af-3cf6-8108-32c6ac20e700',id,0.5,'TEASPOON',NULL,10 FROM product_templates WHERE id='f960beee-dc58-3e81-b4e2-da7f1feb354e';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '97d7ad68-d8de-352e-9d77-f12338e12a50','cf89624f-31af-3cf6-8108-32c6ac20e700',id,6,'GRINDER_TURN',NULL,11 FROM product_templates WHERE id='2c65e27d-5cac-386d-a8e2-b56ccf62205f';
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('5196ec60-4d31-3c63-8b49-da1cecb3a7f7','cf89624f-31af-3cf6-8108-32c6ac20e700',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Skræl "
  }, {
    "recipeIngredientId" : "59671942-0aa7-390f-8cb5-e9920b8df0cc"
  }, {
    "text" : ". Skær store kartofler over, så stykkerne er nogenlunde lige store."
  } ]
}'::jsonb,1);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('39e80aa0-dbb1-365e-b465-59e78f88867d','cf89624f-31af-3cf6-8108-32c6ac20e700',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Skræl "
  }, {
    "recipeIngredientId" : "cb423199-2e44-3ece-b93c-f152afca36a3"
  }, {
    "text" : " og skær dem i cirka 2 cm stykker."
  } ]
}'::jsonb,2);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('52f26cb9-c22f-31bf-97ea-6ca0e5848f3b','cf89624f-31af-3cf6-8108-32c6ac20e700',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Kom kartofler og gulerødder i en gryde. Hæld koldt vand i, til det står cirka 2 cm over grøntsagerne. Tilsæt "
  }, {
    "recipeIngredientId" : "991469bf-20af-3bb4-9bdc-41555088be03",
    "quantity" : 0.125,
    "unit" : "TEASPOON"
  }, {
    "text" : ". Sæt låg på gryden, men tænd ikke for komfuret endnu."
  } ]
}'::jsonb,3);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('811053b0-4e19-3211-90b2-1f911834e700','cf89624f-31af-3cf6-8108-32c6ac20e700',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Pil "
  }, {
    "recipeIngredientId" : "57425492-1ade-31ba-841c-704944804078"
  }, {
    "text" : " og skær det i små tern på cirka ½ cm."
  } ]
}'::jsonb,4);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('3687ca1f-f126-32bb-863f-81242dbbbbd2','cf89624f-31af-3cf6-8108-32c6ac20e700',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Del "
  }, {
    "recipeIngredientId" : "45c88ca0-5e6d-3d60-94ab-567060849736"
  }, {
    "text" : " i portioner på cirka 100 g og form dem til runde bøffer på cirka 1½–2 cm tykkelse. Tryk kødet sammen uden at ælte det."
  } ]
}'::jsonb,5);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('8b785fe3-9574-34a8-b7b3-523bc2f35652','cf89624f-31af-3cf6-8108-32c6ac20e700','[structured instruction]','{
  "parts" : [ {
    "text" : "Sæt gryden med kartofler og gulerødder på trin 9 med låg. Når vandet koger tydeligt, skru ned til trin 6 og kog i 20 minutter."
  } ]
}'::jsonb,1,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('667a2b6d-c939-363f-a9ca-78107852d165','cf89624f-31af-3cf6-8108-32c6ac20e700','[structured instruction]','{
  "parts" : [ {
    "text" : "Fordel "
  }, {
    "recipeIngredientId" : "991469bf-20af-3bb4-9bdc-41555088be03",
    "quantity" : 0.125,
    "unit" : "TEASPOON"
  }, {
    "text" : " og "
  }, {
    "recipeIngredientId" : "97d7ad68-d8de-352e-9d77-f12338e12a50",
    "quantity" : 3,
    "unit" : "GRINDER_TURN"
  }, {
    "text" : " over den ene side af hakkebøfferne."
  } ]
}'::jsonb,2,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('f2dfbfe4-802f-30fd-940a-26c1c20c07ce','cf89624f-31af-3cf6-8108-32c6ac20e700','Sæt en stor tør pande på trin 7 og varm den i 2 minutter.',NULL,3,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('41791657-bb31-3c67-a553-aed7e954c7c7','cf89624f-31af-3cf6-8108-32c6ac20e700','[structured instruction]','{
  "parts" : [ {
    "text" : "Læg hakkebøfferne på panden med den krydrede side nedad. Fordel "
  }, {
    "recipeIngredientId" : "991469bf-20af-3bb4-9bdc-41555088be03",
    "quantity" : 0.125,
    "unit" : "TEASPOON"
  }, {
    "text" : " og "
  }, {
    "recipeIngredientId" : "97d7ad68-d8de-352e-9d77-f12338e12a50",
    "quantity" : 3,
    "unit" : "GRINDER_TURN"
  }, {
    "text" : " over oversiden. Steg bøfferne i 3 minutter uden at flytte dem."
  } ]
}'::jsonb,4,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('9bf54102-92f3-34f7-8a4d-71be24a4aeba','cf89624f-31af-3cf6-8108-32c6ac20e700','Vend hakkebøfferne og steg dem i 3 minutter på den anden side på trin 7.',NULL,5,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('9b4c28cd-7c9c-3e10-a6b6-f800c08652ac','cf89624f-31af-3cf6-8108-32c6ac20e700','Mål kernetemperaturen fra siden ind i midten af den tykkeste bøf. Lad bøfferne fortsætte på trin 7, indtil kernetemperaturen når 70 °C. Tag derefter bøfferne af panden og læg dem på en ren tallerken. Behold stegeresterne på panden.',NULL,6,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('62d668a3-e245-395b-83ea-6fdba4699554','cf89624f-31af-3cf6-8108-32c6ac20e700','Når kartofler og gulerødder har kogt i 20 minutter, stik en lille kniv i en af de største kartofler. Når kniven glider let igennem, hæld vandet fra og sæt gryden tilbage uden låg.',NULL,7,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('eb6dcbeb-981d-3338-9146-c50f774b435b','cf89624f-31af-3cf6-8108-32c6ac20e700','[structured instruction]','{
  "parts" : [ {
    "text" : "Sæt panden på trin 6. Tilsæt "
  }, {
    "recipeIngredientId" : "f499fcfe-78a2-3657-a405-2c147aaebaf8"
  }, {
    "text" : ". Når smørret er smeltet, tilsæt "
  }, {
    "recipeIngredientId" : "57425492-1ade-31ba-841c-704944804078"
  }, {
    "text" : " og steg i 2 minutter. Rør rundt cirka hvert 30. sekund."
  } ]
}'::jsonb,8,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('4d92e6ad-863b-34ce-8189-e6af22e5de41','cf89624f-31af-3cf6-8108-32c6ac20e700','[structured instruction]','{
  "parts" : [ {
    "text" : "Tilsæt "
  }, {
    "recipeIngredientId" : "8b88a056-b806-31ca-94e1-6e7459d8504a"
  }, {
    "text" : " og rør mel, løg, smør og stegerester grundigt sammen i 30 sekunder."
  } ]
}'::jsonb,9,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('5b9cc1f0-d38a-34f4-b30e-3984b6ae4172','cf89624f-31af-3cf6-8108-32c6ac20e700','[structured instruction]','{
  "parts" : [ {
    "text" : "Skru ned til trin 5. Tilsæt først "
  }, {
    "recipeIngredientId" : "b8f8cd4c-d5d0-3af1-847e-4f328f8ca255",
    "quantity" : 0.5,
    "unit" : "DECILITER"
  }, {
    "text" : " og rør konstant i 30 sekunder."
  } ]
}'::jsonb,10,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('a58cfb9f-5a80-38af-af29-687025e5746c','cf89624f-31af-3cf6-8108-32c6ac20e700','[structured instruction]','{
  "parts" : [ {
    "text" : "Tilsæt yderligere "
  }, {
    "recipeIngredientId" : "b8f8cd4c-d5d0-3af1-847e-4f328f8ca255",
    "quantity" : 0.5,
    "unit" : "DECILITER"
  }, {
    "text" : " og rør konstant i 30 sekunder."
  } ]
}'::jsonb,11,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('cf4fa4e5-7f93-3a71-a5c8-800d2854193c','cf89624f-31af-3cf6-8108-32c6ac20e700','[structured instruction]','{
  "parts" : [ {
    "text" : "Tilsæt de sidste "
  }, {
    "recipeIngredientId" : "b8f8cd4c-d5d0-3af1-847e-4f328f8ca255",
    "quantity" : 0.5,
    "unit" : "DECILITER"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "5e20c32d-9dff-381e-9a61-aa28a7c3d621"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "c2020bc2-7540-3a2b-bdb9-fd19df02b71d"
  }, {
    "text" : " og "
  }, {
    "recipeIngredientId" : "991469bf-20af-3bb4-9bdc-41555088be03",
    "quantity" : 0.125,
    "unit" : "TEASPOON"
  }, {
    "text" : ". Rør grundigt i 30 sekunder."
  } ]
}'::jsonb,12,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('6e72986a-1a48-39d2-96e3-c5f7e07b6f08','cf89624f-31af-3cf6-8108-32c6ac20e700','Lad sovsen varme på trin 5, til den småbobler. Lad den derefter småboble i 2 minutter og rør cirka hvert 30. sekund.',NULL,13,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('8ed711ef-de70-3852-b41c-8d6da904fba8','cf89624f-31af-3cf6-8108-32c6ac20e700','Læg hakkebøfferne og saften fra tallerkenen tilbage i sovsen. Hold panden på trin 4 i 2 minutter og vend bøfferne efter 1 minut.',NULL,14,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('df099dcf-ebeb-36f9-b66f-ca922295acae','cf89624f-31af-3cf6-8108-32c6ac20e700','Sluk for panden og server hakkebøfferne med kartofler, gulerødder og brun pandesovs.',NULL,15,'TEXT',NULL);
COMMIT;
