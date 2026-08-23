package dk.jamesbabz.madkursus.service.models;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RecipeUnitTest {
    @Test void convertsKnownVolumeUnitsExactly() {
        assertThat(RecipeUnit.DECILITER.toMilliliters(BigDecimal.ONE)).contains(new BigDecimal("100"));
        assertThat(RecipeUnit.TABLESPOON.toMilliliters(BigDecimal.ONE)).contains(new BigDecimal("15"));
        assertThat(RecipeUnit.TEASPOON.toMilliliters(BigDecimal.ONE)).contains(new BigDecimal("5"));
        assertThat(RecipeUnit.MILLILITER.toMilliliters(new BigDecimal("1.5"))).contains(new BigDecimal("1.5"));
    }
    @Test void neverInventsWeightOrPieceConversions() {
        assertThat(RecipeUnit.GRAM.toMilliliters(BigDecimal.ONE)).isEmpty();
        assertThat(RecipeUnit.PIECE.toMilliliters(BigDecimal.ONE)).isEmpty();
        assertThat(RecipeUnit.GRINDER_TURN.toMilliliters(BigDecimal.TEN)).isEmpty();
    }
}
