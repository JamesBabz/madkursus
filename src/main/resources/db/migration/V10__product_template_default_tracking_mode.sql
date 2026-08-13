ALTER TABLE product_templates
    ADD COLUMN default_tracking_mode VARCHAR(16) NOT NULL DEFAULT 'QUANTITY',
    ADD CONSTRAINT product_templates_default_tracking_mode_check
        CHECK (default_tracking_mode IN ('QUANTITY', 'PRESENCE'));

-- Spices are normally replenished by presence rather than by measured pantry balance.
UPDATE product_templates SET default_tracking_mode = 'PRESENCE' WHERE category = 'SPICE';

UPDATE product_templates SET default_tracking_mode = 'PRESENCE' WHERE name IN (
    'Herbes de Provence', 'Laurbærblade', 'Tørret basilikum', 'Tørret dild', 'Tørret estragon',
    'Tørret merian', 'Tørret oregano', 'Tørret persille', 'Tørret rosmarin', 'Tørret salvie',
    'Tørret timian',
    'Barbecuesauce', 'Dijonsennep', 'Fiskesauce', 'Hoisinsauce', 'Ketchup', 'Mayonnaise',
    'Remoulade', 'Sennep', 'Sojasauce', 'Sriracha', 'Sweet chili sauce', 'Tabasco',
    'Teriyakisauce', 'Worcestershire sauce', 'Østerssauce',
    'Kokosolie', 'Neutral olie', 'Olivenolie', 'Rapsolie', 'Sesamolie', 'Solsikkeolie',
    'Balsamico', 'Hvidvinseddike', 'Lagereddike', 'Riseddike', 'Rødvinseddike', 'Æblecidereddike',
    'Fiskebouillon', 'Grøntsagsbouillon', 'Grøntsagsfond', 'Hønsebouillon', 'Kyllingefond',
    'Oksebouillon', 'Oksefond', 'Tomatpuré', 'Honning', 'Vaniljeekstrakt', 'Madkulør'
);
