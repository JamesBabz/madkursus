ALTER TABLE product_templates DROP CONSTRAINT product_templates_default_tracking_mode_check;
ALTER TABLE product_templates ADD CONSTRAINT product_templates_default_tracking_mode_check
    CHECK (default_tracking_mode IN ('QUANTITY', 'PRESENCE', 'UNTRACKED'));

ALTER TABLE products DROP CONSTRAINT products_inventory_tracking_mode_check;
ALTER TABLE products ADD CONSTRAINT products_inventory_tracking_mode_check
    CHECK (inventory_tracking_mode IN ('QUANTITY', 'PRESENCE', 'UNTRACKED'));

INSERT INTO product_templates (id, name, normalized_name, category, default_unit, common, default_tracking_mode)
VALUES ('04a53a53-364c-373c-8fe9-68e4146652d4', 'Vand', 'vand', 'OTHER', 'MILLILITER', TRUE, 'UNTRACKED');

INSERT INTO product_template_aliases (template_id, alias, normalized_alias)
VALUES ('04a53a53-364c-373c-8fe9-68e4146652d4', 'postevand', 'postevand');
