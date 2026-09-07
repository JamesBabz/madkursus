package dk.jamesbabz.madkursus.service.models;

import java.math.BigDecimal;

/** Internal normalized nutrient value. The amount is always explicit about its quantity/unit basis. */
public record NutritionData(BigDecimal carbohydrateGrams, BigDecimal basisQuantity, RecipeUnit basisUnit,
                            String source, String provider, String externalFoodId, String sourceVersion,
                            String sourceUrl, String note) {
    public NutritionData(BigDecimal carbohydrateGrams,BigDecimal basisQuantity,RecipeUnit basisUnit,String source){this(carbohydrateGrams,basisQuantity,basisUnit,source,null,null,null,null,null);}
    public NutritionData {
        if (carbohydrateGrams == null || carbohydrateGrams.signum() < 0 || basisQuantity == null
                || basisQuantity.signum() <= 0 || basisUnit == null)
            throw new IllegalArgumentException("Nutrition data requires a non-negative value and a positive explicit basis");
        if(source==null||source.isBlank())throw new IllegalArgumentException("Nutrition source is required");
    }
}
