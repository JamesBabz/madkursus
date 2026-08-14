package dk.jamesbabz.madkursus.service.models;

import java.math.BigDecimal;

public record NormalizedRecipeQuantity(BigDecimal quantity, Unit unit, String warning) {
    public boolean resolved() { return warning == null; }
}
