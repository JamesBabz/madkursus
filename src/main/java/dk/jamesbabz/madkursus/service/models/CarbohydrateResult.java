package dk.jamesbabz.madkursus.service.models;

import java.math.BigDecimal;
import java.util.List;

public record CarbohydrateResult(BigDecimal perPortionGrams, BigDecimal totalGrams, boolean complete,
                                 int unknownIngredientCount, List<CarbohydrateIngredientResult> ingredients) {
    public CarbohydrateResult { ingredients = List.copyOf(ingredients); }
    public BigDecimal carbohydratesForConsumedPortions(BigDecimal consumedPortions){if(consumedPortions==null||consumedPortions.signum()<0)throw new IllegalArgumentException("Consumed portions cannot be negative");return perPortionGrams.multiply(consumedPortions);}
}
