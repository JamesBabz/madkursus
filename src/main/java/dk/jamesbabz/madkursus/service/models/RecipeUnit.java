package dk.jamesbabz.madkursus.service.models;

import java.math.BigDecimal;
import java.util.Optional;

public enum RecipeUnit {
    GRAM(null), MILLILITER(BigDecimal.ONE), PIECE(null), TEASPOON(BigDecimal.valueOf(5)),
    TABLESPOON(BigDecimal.valueOf(15)), DECILITER(BigDecimal.valueOf(100)), GRINDER_TURN(null);
    private final BigDecimal milliliters;
    RecipeUnit(BigDecimal milliliters) { this.milliliters = milliliters; }
    public Optional<BigDecimal> toMilliliters(BigDecimal quantity) {
        return milliliters == null ? Optional.empty() : Optional.of(quantity.multiply(milliliters));
    }
    public Optional<BigDecimal> convert(BigDecimal quantity,RecipeUnit target) {
        if(this==target)return Optional.of(quantity);
        if(milliliters==null||target.milliliters==null)return Optional.empty();
        return Optional.of(quantity.multiply(milliliters).divide(target.milliliters,java.math.MathContext.DECIMAL128));
    }
    public static RecipeUnit storage(Unit unit){return switch(unit){case GRAM->GRAM;case MILLILITER->MILLILITER;case PIECE->PIECE;};}
}
