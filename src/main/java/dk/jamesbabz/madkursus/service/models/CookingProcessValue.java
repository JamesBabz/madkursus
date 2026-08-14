package dk.jamesbabz.madkursus.service.models;

import java.math.BigDecimal;

public record CookingProcessValue(BigDecimal quantity, RecipeUnit unit, Integer durationSeconds,
        Integer temperatureCelsius, HeatLevel heatLevel, BigDecimal number, String text) {
    public static CookingProcessValue empty() { return new CookingProcessValue(null,null,null,null,null,null,null); }
}
