CREATE TABLE product_template_unit_conversions (
    template_id UUID NOT NULL REFERENCES product_templates(id) ON DELETE CASCADE,
    from_unit VARCHAR(32) NOT NULL,
    to_unit VARCHAR(32) NOT NULL,
    factor NUMERIC NOT NULL CHECK (factor > 0),
    PRIMARY KEY (template_id, from_unit, to_unit),
    CHECK (from_unit IN ('GRAM','MILLILITER','PIECE','TEASPOON','TABLESPOON','DECILITER','GRINDER_TURN')),
    CHECK (to_unit IN ('GRAM','MILLILITER','PIECE','TEASPOON','TABLESPOON','DECILITER','GRINDER_TURN')),
    CHECK (from_unit <> to_unit)
);

-- Practical Danish kitchen convention: one level tablespoon of wheat flour is approximately 9 grams.
INSERT INTO product_template_unit_conversions(template_id,from_unit,to_unit,factor)
VALUES ('79d3cfe2-9723-3844-b21f-f7b543d13aa1','TABLESPOON','GRAM',9);
