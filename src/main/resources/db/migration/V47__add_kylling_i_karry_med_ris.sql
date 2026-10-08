-- Generated locally. Review and test before deployment.
-- Existing copied Recipes are intentionally untouched.
BEGIN;
INSERT INTO recipe_templates(id,name,normalized_name,description,active,created_at,updated_at) VALUES ('55871139-31ad-33a0-ae61-ff3439dde75d','Kylling i karrysovs med ris','kylling i karrysovs med ris','Kyllingebryst med løg og gulerødder i en cremet karrysovs, serveret med basmatiris. Mængder til én stor portion. Bouillonmængden er estimeret ud fra en terning på 10 g.',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON CONFLICT(id) DO UPDATE SET name=EXCLUDED.name,normalized_name=EXCLUDED.normalized_name,description=EXCLUDED.description,active=true,updated_at=CURRENT_TIMESTAMP;
INSERT INTO recipe_template_equipment_requirements(id,recipe_template_id,equipment_type,label,sort_order) VALUES ('012bf205-639a-39cf-a8e6-c3763f6bba4e','55871139-31ad-33a0-ae61-ff3439dde75d','STOVE',NULL,1);
INSERT INTO recipe_template_equipment_requirements(id,recipe_template_id,equipment_type,label,sort_order) VALUES ('5039d022-42ac-330d-825a-6460d2fd3388','55871139-31ad-33a0-ae61-ff3439dde75d','PAN',NULL,2);
INSERT INTO recipe_template_equipment_requirements(id,recipe_template_id,equipment_type,label,sort_order) VALUES ('efac8049-054d-3411-af9c-e69d6a05122c','55871139-31ad-33a0-ae61-ff3439dde75d','POT',NULL,3);
INSERT INTO recipe_template_equipment_requirements(id,recipe_template_id,equipment_type,label,sort_order) VALUES ('c9e3abf7-fdf0-30b2-a51f-814306360594','55871139-31ad-33a0-ae61-ff3439dde75d','THERMOMETER',NULL,4);
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '1ec3e14e-1631-3e6d-9317-7953ed466a71','55871139-31ad-33a0-ae61-ff3439dde75d',id,250,'GRAM',NULL,1 FROM product_templates WHERE id='47814405-2163-3beb-b98e-5c31ad175fa8';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'b6007d2b-85dc-37bf-bdb5-fb72e0edb652','55871139-31ad-33a0-ae61-ff3439dde75d',id,90,'GRAM',NULL,2 FROM product_templates WHERE id='561f6976-2eae-3d18-a069-b243641f1583';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'c5aa9b18-2e77-3b37-b5ed-83365891363b','55871139-31ad-33a0-ae61-ff3439dde75d',id,0.5,'PIECE',NULL,3 FROM product_templates WHERE id='971312c9-0d64-3d48-877a-e8c17977e523';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'c412551e-ebb0-3f92-84bc-fcf740ee3118','55871139-31ad-33a0-ae61-ff3439dde75d',id,100,'GRAM',NULL,4 FROM product_templates WHERE id='735c8e27-2644-308b-96ed-5db2e850e080';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'ae57b660-b90d-3f48-814b-57f64387a49c','55871139-31ad-33a0-ae61-ff3439dde75d',id,125,'MILLILITER',NULL,5 FROM product_templates WHERE id='7e77a990-41ec-32bf-94c8-72d948dec0e0';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '07fae171-8319-30d7-8374-3d1ef587acd3','55871139-31ad-33a0-ae61-ff3439dde75d',id,75,'MILLILITER',NULL,6 FROM product_templates WHERE id='bfe6512e-55cb-39bd-a8c8-89f6a068285d';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '3a7d2957-6369-39bf-954f-5d0467203a11','55871139-31ad-33a0-ae61-ff3439dde75d',id,7.5,'GRAM',NULL,7 FROM product_templates WHERE id='40867ff0-8b5e-3e4b-a4c2-67596c15aef6';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '6a67d8be-fb9d-38b9-a4b5-05d31e596ae5','55871139-31ad-33a0-ae61-ff3439dde75d',id,0.5,'TABLESPOON',NULL,8 FROM product_templates WHERE id='79d3cfe2-9723-3844-b21f-f7b543d13aa1';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '212e111a-5cfc-30f8-92bb-c2d8673516ca','55871139-31ad-33a0-ae61-ff3439dde75d',id,1,'TEASPOON',NULL,9 FROM product_templates WHERE id='7f2b5604-0561-3c1d-82af-00d47991c545';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'd20a1c27-9f0d-328b-b877-4d9179f09c70','55871139-31ad-33a0-ae61-ff3439dde75d',id,2.5,'GRAM',NULL,10 FROM product_templates WHERE id='ddc5ba52-cb9c-3509-98a3-f34266ab8eb5';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'b06f3b20-3895-30f0-95d0-13bc7a3e37a3','55871139-31ad-33a0-ae61-ff3439dde75d',id,0.375,'TEASPOON',NULL,11 FROM product_templates WHERE id='f960beee-dc58-3e81-b4e2-da7f1feb354e';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'b0c63b4e-6f68-3025-a6ca-ee3be6cf04bf','55871139-31ad-33a0-ae61-ff3439dde75d',id,4,'GRINDER_TURN',NULL,12 FROM product_templates WHERE id='2c65e27d-5cac-386d-a8e2-b56ccf62205f';
INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT 'a014ef2f-68a9-3399-a07d-8499b3adbe16','55871139-31ad-33a0-ae61-ff3439dde75d',id,0.5,'TABLESPOON',NULL,13 FROM product_templates WHERE id='ce84c904-58c9-3c93-b8af-35eb4acd1499';
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('18db1a23-8da5-3276-997b-69a64a79bb77','55871139-31ad-33a0-ae61-ff3439dde75d',NULL,'Find en stor stegepande, en gryde med låg, skærebræt, kniv, grydeske, piskeris, køkkenvægt, målebæger og måleskeer frem.',NULL,1);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('64a63c82-98e2-3040-ab7d-18a830b1bf6c','55871139-31ad-33a0-ae61-ff3439dde75d',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Skær "
  }, {
    "recipeIngredientId" : "1ec3e14e-1631-3e6d-9317-7953ed466a71"
  }, {
    "text" : " i tern på ca. 2 × 2 cm. Vask hænder, kniv og skærebræt grundigt bagefter."
  } ]
}'::jsonb,2);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('6ae10cfa-4eac-301a-9e1d-f20164c9a289','55871139-31ad-33a0-ae61-ff3439dde75d',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Pil og hak "
  }, {
    "recipeIngredientId" : "c5aa9b18-2e77-3b37-b5ed-83365891363b"
  }, {
    "text" : " i tern på ca. ½ cm."
  } ]
}'::jsonb,3);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('e2b99716-f551-30b7-ab9a-4706dc800595','55871139-31ad-33a0-ae61-ff3439dde75d',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Skræl "
  }, {
    "recipeIngredientId" : "c412551e-ebb0-3f92-84bc-fcf740ee3118"
  }, {
    "text" : " og skær dem på tværs i runde skiver på ca. 3 mm."
  } ]
}'::jsonb,4);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('f1ba3666-56d3-3f7b-8c25-44e29f4c06ae','55871139-31ad-33a0-ae61-ff3439dde75d',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Afvej "
  }, {
    "recipeIngredientId" : "b6007d2b-85dc-37bf-bdb5-fb72e0edb652"
  }, {
    "text" : " og mål "
  }, {
    "scaledNumber" : 180
  }, {
    "text" : " ml koldt vand pr. portion op. Kom ris, vand og "
  }, {
    "recipeIngredientId" : "b06f3b20-3895-30f0-95d0-13bc7a3e37a3",
    "quantity" : 0.125,
    "unit" : "TEASPOON"
  }, {
    "text" : " i gryden. Læg låget klar."
  } ]
}'::jsonb,5);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('a243b36d-1311-3032-a070-70812d4c194a','55871139-31ad-33a0-ae61-ff3439dde75d',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Mål "
  }, {
    "recipeIngredientId" : "ae57b660-b90d-3f48-814b-57f64387a49c"
  }, {
    "text" : " og "
  }, {
    "recipeIngredientId" : "07fae171-8319-30d7-8374-3d1ef587acd3"
  }, {
    "text" : " op, gerne i samme målebæger."
  } ]
}'::jsonb,6);
INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('fd43385d-5b23-35f3-869c-19e2372cd0d6','55871139-31ad-33a0-ae61-ff3439dde75d',NULL,'[structured instruction]','{
  "parts" : [ {
    "text" : "Mål "
  }, {
    "recipeIngredientId" : "212e111a-5cfc-30f8-92bb-c2d8673516ca"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "6a67d8be-fb9d-38b9-a4b5-05d31e596ae5"
  }, {
    "text" : ", "
  }, {
    "recipeIngredientId" : "3a7d2957-6369-39bf-954f-5d0467203a11"
  }, {
    "text" : " og "
  }, {
    "recipeIngredientId" : "a014ef2f-68a9-3399-a07d-8499b3adbe16"
  }, {
    "text" : " op. Stil bouillon, resten af saltet og peber klar."
  } ]
}'::jsonb,7);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('d2f986da-69a8-3923-b99a-a8d07a5428a6','55871139-31ad-33a0-ae61-ff3439dde75d','Sæt gryden med ris og vand på komfuret ved 9/9 med låg på. Når vandet koger tydeligt, skru ned til 2/9. Lad risene simre under låg i 12 minutter. Sluk derefter, flyt gryden til et koldt blus, og lad risene hvile med låg på i 10 minutter.',NULL,1,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('9daf6d3f-b948-3049-a501-091ac40eeb2b','55871139-31ad-33a0-ae61-ff3439dde75d','[structured instruction]','{
  "parts" : [ {
    "text" : "Varm den store stegepande op på 7/9 i 2 minutter. Tilsæt "
  }, {
    "recipeIngredientId" : "a014ef2f-68a9-3399-a07d-8499b3adbe16"
  }, {
    "text" : " og vent 30 sekunder. Fordel "
  }, {
    "recipeIngredientId" : "1ec3e14e-1631-3e6d-9317-7953ed466a71"
  }, {
    "text" : " på panden. Steg 2 minutter uden at røre, vend stykkerne, og steg yderligere 3 minutter, mens du vender dem et par gange. Flyt kyllingen til en ren tallerken. Den færdigtilberedes senere i sovsen."
  } ]
}'::jsonb,2,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('785ca863-05fb-389e-99fa-93a2d7e2e669','55871139-31ad-33a0-ae61-ff3439dde75d','[structured instruction]','{
  "parts" : [ {
    "text" : "Skru ned til 6/9. Tilsæt "
  }, {
    "recipeIngredientId" : "c5aa9b18-2e77-3b37-b5ed-83365891363b"
  }, {
    "text" : " og "
  }, {
    "recipeIngredientId" : "c412551e-ebb0-3f92-84bc-fcf740ee3118"
  }, {
    "text" : ". Steg i 4 minutter, og rør cirka hvert 30. sekund. Tilsæt "
  }, {
    "recipeIngredientId" : "3a7d2957-6369-39bf-954f-5d0467203a11"
  }, {
    "text" : " og lad det smelte i 30 sekunder. Tilsæt "
  }, {
    "recipeIngredientId" : "212e111a-5cfc-30f8-92bb-c2d8673516ca"
  }, {
    "text" : " og rør i 30 sekunder."
  } ]
}'::jsonb,3,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('3cf1b3d9-e982-3eca-b0c7-b03e0e28d073','55871139-31ad-33a0-ae61-ff3439dde75d','[structured instruction]','{
  "parts" : [ {
    "text" : "Drys "
  }, {
    "recipeIngredientId" : "6a67d8be-fb9d-38b9-a4b5-05d31e596ae5"
  }, {
    "text" : " over grøntsagerne og rør i 30 sekunder. Skru ned til 5/9. Hæld først lidt af den opmålte mælk/fløde-blanding på panden under omrøring. Tilsæt derefter resten lidt ad gangen, indtil al "
  }, {
    "recipeIngredientId" : "ae57b660-b90d-3f48-814b-57f64387a49c"
  }, {
    "text" : " og "
  }, {
    "recipeIngredientId" : "07fae171-8319-30d7-8374-3d1ef587acd3"
  }, {
    "text" : " er brugt."
  } ]
}'::jsonb,4,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('8e117051-b6c6-3dd6-8751-415707b54878','55871139-31ad-33a0-ae61-ff3439dde75d','[structured instruction]','{
  "parts" : [ {
    "text" : "Smuldr "
  }, {
    "recipeIngredientId" : "d20a1c27-9f0d-328b-b877-4d9179f09c70"
  }, {
    "text" : " i sovsen. Tilsæt "
  }, {
    "recipeIngredientId" : "b06f3b20-3895-30f0-95d0-13bc7a3e37a3",
    "quantity" : 0.25,
    "unit" : "TEASPOON"
  }, {
    "text" : " og "
  }, {
    "recipeIngredientId" : "b0c63b4e-6f68-3025-a6ca-ee3be6cf04bf"
  }, {
    "text" : ". Rør grundigt, indtil bouillonen er opløst, og varm sovsen op, til den bobler forsigtigt."
  } ]
}'::jsonb,5,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('dec5c53e-8ce4-30da-8489-8a81b3ef348c','55871139-31ad-33a0-ae61-ff3439dde75d','Kom kyllingen og eventuel saft fra tallerkenen tilbage i sovsen. Skru ned til 4/9. Lad retten simre forsigtigt i 8 minutter, og rør hvert andet minut. Kontrollér det største kyllingestykke med et stegetermometer. Kernetemperaturen skal være mindst 75 °C. Hvis den er lavere, fortsæt tilberedningen og mål igen.',NULL,6,'TEXT',NULL);
INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('6a6feebc-b67a-336f-9d50-12066891e721','55871139-31ad-33a0-ae61-ff3439dde75d','Sluk for varmen. Løsn de færdige ris forsigtigt med en gaffel, og servér kylling og karrysovs ovenpå eller ved siden af risene.',NULL,7,'TEXT',NULL);
COMMIT;
