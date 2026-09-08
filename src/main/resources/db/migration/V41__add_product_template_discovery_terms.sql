-- Curated soft-search membership. These terms do NOT establish stock substitution,
-- recipe ingredient equivalence, aliases, or unit conversion.
CREATE TABLE product_template_discovery_terms (
    term VARCHAR(80) NOT NULL CHECK (term = lower(trim(term)) AND term <> ''),
    template_id UUID NOT NULL REFERENCES product_templates(id) ON DELETE CASCADE,
    PRIMARY KEY (term, template_id)
);

-- Chicken meat discovery includes the existing cuts, whole and minced chicken.
-- Stock/bouillon are deliberately not members of this meal preference group.
INSERT INTO product_template_discovery_terms (term, template_id) VALUES
('kylling', '51c455db-8d4d-372b-8bdc-53d14a3b153b'), -- Hakket kylling
('kylling', 'f083085b-67c6-3e98-a012-6fa9cd42f3c6'), -- Hel kylling
('kylling', '47814405-2163-3beb-b98e-5c31ad175fa8'), -- Kyllingebryst
('kylling', 'e068833d-9132-3b3e-9850-66a1cb1ed016'), -- Kyllingelår
('kylling', 'a75be753-4a3b-35db-a96c-4abf952e3e5c'), -- Kyllingeoverlår
('kylling', 'aa24829c-9a1e-39e1-85e6-c3d87fe8458b'), -- Kyllingeunderlår
('kylling', 'd72f6b31-5e75-3f62-96ad-b12d5f067eb8'); -- Kyllingevinger
