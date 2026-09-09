-- Generated locally. Review and test before deployment.
-- Existing copied Recipes are intentionally untouched.
BEGIN;
INSERT INTO recipe_templates(id,name,normalized_name,description,active,created_at,updated_at) VALUES ('73e2e208-a4ef-3517-94a1-ff7c87a79ede','Cremet kyllingepasta i mild tomatsovs','cremet kyllingepasta i mild tomatsovs','Mild pastaret med kylling, rød peberfrugt og cremet tomatsovs. Pasta og sovs serveres separat.',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON CONFLICT(id) DO UPDATE SET name=EXCLUDED.name,normalized_name=EXCLUDED.normalized_name,description=EXCLUDED.description,active=true,updated_at=CURRENT_TIMESTAMP;
INSERT INTO recipe_template_equipment_requirements(id,recipe_template_id,equipment_type,label,sort_order) VALUES ('fef11371-d8cd-3c55-bb6a-fb42f47de788','73e2e208-a4ef-3517-94a1-ff7c87a79ede','STOVE',NULL,1);
INSERT INTO recipe_template_equipment_requirements(id,recipe_template_id,equipment_type,label,sort_order) VALUES ('e12bc4b5-ffc6-3ec2-9452-db2b10a610c6','73e2e208-a4ef-3517-94a1-ff7c87a79ede','POT',NULL,2);
INSERT INTO recipe_template_equipment_requirements(id,recipe_template_id,equipment_type,label,sort_order) VALUES ('1987cd4f-baed-30df-810f-6343f6aab4e6','73e2e208-a4ef-3517-94a1-ff7c87a79ede','PAN',NULL,3);
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '57c3ae66-0b17-33ea-b497-522944c5b7a3','73e2e208-a4ef-3517-94a1-ff7c87a79ede',id,200,'GRAM',NULL,1 FROM product_templates WHERE id='47814405-2163-3beb-b98e-5c31ad175fa8';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '96443bc6-a931-3a62-9cf8-e93c3bd871d9','73e2e208-a4ef-3517-94a1-ff7c87a79ede',id,125,'GRAM',NULL,2 FROM product_templates WHERE id='99f6dde2-0756-3f15-b72c-5fa5fe69d936';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '8625f0f1-63d8-3a77-a91f-f99c1c72d87f','73e2e208-a4ef-3517-94a1-ff7c87a79ede',id,0.5,'PIECE',NULL,3 FROM product_templates WHERE id='971312c9-0d64-3d48-877a-e8c17977e523';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '5c949ea2-8e5d-3f20-805f-7efc64d36d8d','73e2e208-a4ef-3517-94a1-ff7c87a79ede',id,0.5,'PIECE',NULL,4 FROM product_templates WHERE id='95a0ea0e-af54-36fc-9c81-0e14fda9f1bf';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '3039a383-e6b1-374f-bd9c-ac580887fdad','73e2e208-a4ef-3517-94a1-ff7c87a79ede',id,200,'GRAM',NULL,5 FROM product_templates WHERE id='3288d148-be29-3a16-a67c-210665011c47';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'af0b0a37-18c7-3ecb-b440-15d6cd9a83cb','73e2e208-a4ef-3517-94a1-ff7c87a79ede',id,125,'MILLILITER',NULL,6 FROM product_templates WHERE id='7e77a990-41ec-32bf-94c8-72d948dec0e0';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'c50ec186-549f-347f-857c-45058cbd8ba4','73e2e208-a4ef-3517-94a1-ff7c87a79ede',id,0.5,'TABLESPOON',NULL,7 FROM product_templates WHERE id='e571cf3b-8a8e-3e7b-a491-bda2cd6585bd';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '03d6fc04-591c-389e-ad9e-760e8c2bee3e','73e2e208-a4ef-3517-94a1-ff7c87a79ede',id,0.5,'TABLESPOON',NULL,8 FROM product_templates WHERE id='ce84c904-58c9-3c93-b8af-35eb4acd1499';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '1c7899a7-7e52-3115-bf0e-209cb719e3fd','73e2e208-a4ef-3517-94a1-ff7c87a79ede',id,0.5,'TEASPOON',NULL,9 FROM product_templates WHERE id='4fd35177-2edc-3f9f-8a52-0e59525a91aa';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '8bd248bf-7f98-316e-9db3-2020124eb1ac','73e2e208-a4ef-3517-94a1-ff7c87a79ede',id,0.25,'TEASPOON',NULL,10 FROM product_templates WHERE id='4e5a7dab-457f-34b1-a50b-249727a31bb6';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'e51de8ed-d8c3-38e0-95f9-69188b318a61','73e2e208-a4ef-3517-94a1-ff7c87a79ede',id,0.375,'TEASPOON',NULL,11 FROM product_templates WHERE id='f960beee-dc58-3e81-b4e2-da7f1feb354e';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'ca8b1d7e-baa3-344c-ac2a-d83d899c1272','73e2e208-a4ef-3517-94a1-ff7c87a79ede',id,4,'GRINDER_TURN',NULL,12 FROM product_templates WHERE id='2c65e27d-5cac-386d-a8e2-b56ccf62205f';
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('9901b5bf-481e-3213-897c-b6465b71af9c','73e2e208-a4ef-3517-94a1-ff7c87a79ede',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Skær "
  }, {
    "recipeIngredientId" : "57c3ae66-0b17-33ea-b497-522944c5b7a3"
  }, {
    "text" : " i tern på cirka 2 × 2 cm. Vask derefter hænder, kniv og skærebræt."
  } ]
}'::jsonb,1);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('713303f7-0401-31e2-bf99-72a6d6e2bbbd','73e2e208-a4ef-3517-94a1-ff7c87a79ede',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Pil "
  }, {
    "recipeIngredientId" : "8625f0f1-63d8-3a77-a91f-f99c1c72d87f"
  }, {
    "text" : ", halvér det og skær det i små tern på cirka ½ cm."
  } ]
}'::jsonb,2);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('060df318-a470-3cf4-8f06-d6ee3f84d891','73e2e208-a4ef-3517-94a1-ff7c87a79ede',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Skyl "
  }, {
    "recipeIngredientId" : "5c949ea2-8e5d-3f20-805f-7efc64d36d8d"
  }, {
    "text" : ", fjern stilk, kerner og det hvide indeni, og skær den i stykker på cirka 1 × 1 cm."
  } ]
}'::jsonb,3);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('0b4581bc-eb2c-3ca0-9ec3-56f8ec761dc5','73e2e208-a4ef-3517-94a1-ff7c87a79ede',NULL,NULL,1,'PROCESS',(SELECT id FROM cooking_processes WHERE process_key='BOIL_PASTA'));
INSERT INTO recipe_template_process_bindings(id,recipe_template_step_id,parameter_key,recipe_ingredient_id,product_template_id,prepared_component_id,quantity,unit,duration_seconds,temperature_celsius,heat_level,number_value,text_value) VALUES ('d65ad5ff-8c04-3c11-b096-6f3e8dc9588a','0b4581bc-eb2c-3ca0-9ec3-56f8ec761dc5','PASTA','96443bc6-a931-3a62-9cf8-e93c3bd871d9','99f6dde2-0756-3f15-b72c-5fa5fe69d936',NULL,125,'GRAM',NULL,NULL,NULL,NULL,NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('66bb8d6a-3aed-3ccb-87bc-25165e88ac2b','73e2e208-a4ef-3517-94a1-ff7c87a79ede','[structured instruction]','{
  "parts" : [ {
    "text" : "Sæt den store pande på trin 7. Tilsæt "
  }, {
    "recipeIngredientId" : "03d6fc04-591c-389e-ad9e-760e8c2bee3e"
  }, {
    "text" : " og varm olien i 1 minut."
  } ]
}'::jsonb,2,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('732b7e19-b28d-3204-b32a-5235e4d81e17','73e2e208-a4ef-3517-94a1-ff7c87a79ede','[structured instruction]','{
  "parts" : [ {
    "text" : "Kom "
  }, {
    "recipeIngredientId" : "57c3ae66-0b17-33ea-b497-522944c5b7a3"
  }, {
    "text" : " på panden og fordel kødet så godt som muligt i ét lag. Fordel "
  }, {
    "recipeIngredientId" : "e51de8ed-d8c3-38e0-95f9-69188b318a61",
    "quantity" : 0.125,
    "unit" : "TEASPOON"
  }, {
    "text" : " og "
  }, {
    "recipeIngredientId" : "ca8b1d7e-baa3-344c-ac2a-d83d899c1272",
    "quantity" : 2,
    "unit" : "GRINDER_TURN"
  }, {
    "text" : " over kyllingen. Steg i 2 minutter uden at røre."
  } ]
}'::jsonb,3,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('29c51f4f-bf88-3c01-ac2c-2e3ff6322d25','73e2e208-a4ef-3517-94a1-ff7c87a79ede','Vend kyllingen grundigt. Steg yderligere 2 minutter på trin 7, og vend rundt én gang efter 1 minut. Tag derefter kyllingen af panden og læg den på en ren tallerken.',NULL,4,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('a7883afc-9277-3967-9981-c97c7a96a9ad','73e2e208-a4ef-3517-94a1-ff7c87a79ede','[structured instruction]','{
  "parts" : [ {
    "text" : "Behold panden på trin 7. Tilsæt "
  }, {
    "recipeIngredientId" : "8625f0f1-63d8-3a77-a91f-f99c1c72d87f"
  }, {
    "text" : " og steg i 2 minutter. Rør rundt cirka hvert 30. sekund."
  } ]
}'::jsonb,5,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('e28552d6-5504-30e2-848f-ea31833fc174','73e2e208-a4ef-3517-94a1-ff7c87a79ede','[structured instruction]','{
  "parts" : [ {
    "text" : "Tilsæt "
  }, {
    "recipeIngredientId" : "5c949ea2-8e5d-3f20-805f-7efc64d36d8d"
  }, {
    "text" : " og steg videre i 2 minutter på trin 7. Rør rundt cirka hvert 30. sekund."
  } ]
}'::jsonb,6,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('dc71b317-860c-3298-8413-be47db641d58','73e2e208-a4ef-3517-94a1-ff7c87a79ede','[structured instruction]','{
  "parts" : [ {
    "text" : "Skru ned til trin 5. Tilsæt "
  }, {
    "recipeIngredientId" : "c50ec186-549f-347f-857c-45058cbd8ba4"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "1c7899a7-7e52-3115-bf0e-209cb719e3fd"
  }, {
    "text" : " og "
  }, {
    "recipeIngredientId" : "8bd248bf-7f98-316e-9db3-2020124eb1ac"
  }, {
    "text" : ". Rør konstant i 30 sekunder."
  } ]
}'::jsonb,7,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('e0a1c5a0-2b09-36b9-9358-22d90fda7d4a','73e2e208-a4ef-3517-94a1-ff7c87a79ede','[structured instruction]','{
  "parts" : [ {
    "text" : "Tilsæt "
  }, {
    "recipeIngredientId" : "3039a383-e6b1-374f-bd9c-ac580887fdad"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "af0b0a37-18c7-3ecb-b440-15d6cd9a83cb"
  }, {
    "text" : ", de resterende "
  }, {
    "recipeIngredientId" : "e51de8ed-d8c3-38e0-95f9-69188b318a61",
    "quantity" : 0.25,
    "unit" : "TEASPOON"
  }, {
    "text" : " og de resterende "
  }, {
    "recipeIngredientId" : "ca8b1d7e-baa3-344c-ac2a-d83d899c1272",
    "quantity" : 2,
    "unit" : "GRINDER_TURN"
  }, {
    "text" : ". Rør grundigt rundt i 30 sekunder."
  } ]
}'::jsonb,8,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('726724d2-7f0a-358b-b95a-de1c878e61d4','73e2e208-a4ef-3517-94a1-ff7c87a79ede','Vent til sovsen småbobler. Lad den derefter småboble uden låg på trin 5 i 3 minutter. Rør rundt cirka hvert 30. sekund.',NULL,9,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('85013c6b-a629-343f-b12e-476016077541','73e2e208-a4ef-3517-94a1-ff7c87a79ede','Kom kyllingen og saften fra tallerkenen tilbage i sovsen. Rør rundt og lad retten småboble på trin 5 i 3 minutter.',NULL,10,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('3d5836a2-fdb3-32e2-9c3c-4e74db68c031','73e2e208-a4ef-3517-94a1-ff7c87a79ede','Sluk blusset. Server den kogte pasta separat med den cremede kyllinge-tomatsovs ovenpå eller ved siden af.',NULL,11,'TEXT',NULL);
COMMIT;
