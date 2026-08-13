ALTER TABLE products DROP CONSTRAINT products_category_check;
ALTER TABLE products ADD CONSTRAINT products_category_check CHECK (category IN (
    'BAKING','BREAD','DAIRY','EGG','FISH','FROZEN','FRUIT','GRAIN_PASTA','HERB','LEGUME','MEAT',
    'NUT_SEED','OIL_FAT','OTHER','PRESERVED','SAUCE_CONDIMENT','SPICE','STOCK','SWEETENER',
    'VEGETABLE','VINEGAR_ACID','DRY_GOODS'));

CREATE TABLE product_templates (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    normalized_name VARCHAR(255) NOT NULL UNIQUE,
    category VARCHAR(32) NOT NULL,
    default_unit VARCHAR(32) NOT NULL,
    common BOOLEAN NOT NULL,
    CONSTRAINT product_templates_unit_check CHECK (default_unit IN ('GRAM','MILLILITER','PIECE')),
    CONSTRAINT product_templates_category_check CHECK (category IN (
        'BAKING','BREAD','DAIRY','EGG','FISH','FROZEN','FRUIT','GRAIN_PASTA','HERB','LEGUME','MEAT',
        'NUT_SEED','OIL_FAT','OTHER','PRESERVED','SAUCE_CONDIMENT','SPICE','STOCK','SWEETENER',
        'VEGETABLE','VINEGAR_ACID','DRY_GOODS'))
);

CREATE TABLE product_template_aliases (
    template_id UUID NOT NULL REFERENCES product_templates(id) ON DELETE CASCADE,
    alias VARCHAR(255) NOT NULL,
    normalized_alias VARCHAR(255) NOT NULL,
    PRIMARY KEY (template_id, normalized_alias)
);

CREATE INDEX product_templates_common_name_idx ON product_templates(common, normalized_name);
CREATE INDEX product_template_aliases_normalized_idx ON product_template_aliases(normalized_alias);
