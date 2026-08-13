package dk.jamesbabz.madkursus.service.models;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class QuantityRoundingPolicyTest {
    @Test void shoppingListAlwaysRoundsPiecesUpToWholeItems() {
        assertThat(QuantityRoundingPolicy.forShoppingList(new BigDecimal("0.5"))).isEqualByComparingTo("1");
        assertThat(QuantityRoundingPolicy.forShoppingList(new BigDecimal("1.5"))).isEqualByComparingTo("2");
        assertThat(QuantityRoundingPolicy.forShoppingList(new BigDecimal("2.0"))).isEqualByComparingTo("2");
    }

    @Test void shoppingListRoundsGramAndMilliliterAmountsUpToWholeBaseUnits() {
        assertThat(QuantityRoundingPolicy.forShoppingList(new BigDecimal("100.2"))).isEqualByComparingTo("101");
        assertThat(QuantityRoundingPolicy.forShoppingList(new BigDecimal("250.1"))).isEqualByComparingTo("251");
    }

    @Test void inventoryStillAllowsHalfPieceIncrements() {
        assertThat(QuantityRoundingPolicy.forInventory(new BigDecimal("0.5"), Unit.PIECE)).isEqualByComparingTo("0.5");
        assertThat(QuantityRoundingPolicy.forInventory(new BigDecimal("1.5"), Unit.PIECE)).isEqualByComparingTo("1.5");
    }
}
