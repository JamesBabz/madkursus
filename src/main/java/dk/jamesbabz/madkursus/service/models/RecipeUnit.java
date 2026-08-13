package dk.jamesbabz.madkursus.service.models;

import java.math.BigDecimal;
import java.util.Optional;

public enum RecipeUnit {
    GRAM(null), MILLILITER(BigDecimal.ONE), PIECE(null), TEASPOON(BigDecimal.valueOf(5)),
    TABLESPOON(BigDecimal.valueOf(15)), DECILITER(BigDecimal.valueOf(100));
    private final BigDecimal milliliters;
    RecipeUnit(BigDecimal milliliters) { this.milliliters = milliliters; }
    public Optional<BigDecimal> toMilliliters(BigDecimal quantity) {
        return milliliters == null ? Optional.empty() : Optional.of(quantity.multiply(milliliters));
    }
}
