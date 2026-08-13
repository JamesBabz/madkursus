package dk.jamesbabz.madkursus.service.models;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class QuantityRoundingPolicy {
    private static final BigDecimal HALF_PIECE = new BigDecimal("0.5");

    private QuantityRoundingPolicy() {}

    public static BigDecimal forInventory(BigDecimal value, Unit unit) {
        return roundUp(value, unit == Unit.PIECE ? HALF_PIECE : BigDecimal.ONE);
    }

    public static BigDecimal forShoppingList(BigDecimal value) {
        return roundUp(value, BigDecimal.ONE);
    }

    private static BigDecimal roundUp(BigDecimal value, BigDecimal increment) {
        if (value == null || value.signum() == 0) return BigDecimal.ZERO;
        return value.divide(increment, 0, RoundingMode.CEILING).multiply(increment).stripTrailingZeros();
    }
}
