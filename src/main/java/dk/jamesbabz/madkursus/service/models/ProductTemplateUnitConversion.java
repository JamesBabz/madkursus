package dk.jamesbabz.madkursus.service.models;

import java.math.BigDecimal;

/** A deterministic ProductTemplate-owned conversion: one fromUnit equals factor toUnits. */
public record ProductTemplateUnitConversion(RecipeUnit fromUnit,RecipeUnit toUnit,BigDecimal factor) {
    public ProductTemplateUnitConversion {
        if(fromUnit==null||toUnit==null||factor==null||factor.signum()<=0)
            throw new IllegalArgumentException("ProductTemplate unit conversion requires units and a positive factor");
    }
}
