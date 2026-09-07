-- Curated declarations. Zero is explicit metadata, never the absence of metadata.
UPDATE product_templates SET carbohydrate_grams=0,nutrition_basis_quantity=100,nutrition_basis_unit='GRAM',
 nutrition_source='DTU Frida: salt, available carbohydrate declaration',nutrition_provider='DTU',nutrition_note='Known zero'
WHERE id='f960beee-dc58-3e81-b4e2-da7f1feb354e';
UPDATE product_templates SET carbohydrate_grams=0,nutrition_basis_quantity=100,nutrition_basis_unit='MILLILITER',
 nutrition_source='Nutrition declaration for pure rapeseed oil',nutrition_provider='MANUAL',nutrition_note='Known zero'
WHERE id='ce84c904-58c9-3c93-b8af-35eb4acd1499';
UPDATE product_templates SET carbohydrate_grams=4.4,nutrition_basis_quantity=100,nutrition_basis_unit='GRAM',
 nutrition_source='DTU Frida food 559, Gulerod, dansk, rå; accessed 2026-08-25',nutrition_provider='DTU',nutrition_external_food_id='559'
WHERE id='735c8e27-2644-308b-96ed-5db2e850e080';
