package dk.jamesbabz.madkursus.service.models;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Danish quantity rendering shared with structured recipe instructions. */
public final class QuantityDisplay {
    private QuantityDisplay() {}
    public static String format(BigDecimal value, Unit unit) { return format(value, RecipeUnit.storage(unit)); }
    public static String format(BigDecimal value, RecipeUnit unit) {
        if (unit == RecipeUnit.GRINDER_TURN) value = value.setScale(0, RoundingMode.HALF_UP);
        return decimal(value) + " " + switch (unit) {
            case GRAM -> "g"; case MILLILITER -> "ml"; case DECILITER -> "dl";
            case TEASPOON -> "tsk"; case TABLESPOON -> "spsk"; case PIECE -> "stk";
            case GRINDER_TURN -> value.compareTo(BigDecimal.ONE) == 0 ? "omgang" : "omgange";
        };
    }
    public static String decimal(BigDecimal value) {
        value = value.stripTrailingZeros();
        if (value.scale() <= 0) return value.toPlainString();
        BigDecimal whole = value.setScale(0, RoundingMode.DOWN), fraction = value.subtract(whole);
        String glyph = fraction.compareTo(new BigDecimal("0.25")) == 0 ? "¼"
                : fraction.compareTo(new BigDecimal("0.5")) == 0 ? "½"
                : fraction.compareTo(new BigDecimal("0.75")) == 0 ? "¾" : null;
        if (glyph != null) return whole.signum() == 0 ? glyph : whole.toPlainString() + glyph;
        return value.toPlainString().replace('.', ',');
    }
}
