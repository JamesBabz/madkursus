package dk.jamesbabz.madkursus.service.applications;

import dk.jamesbabz.madkursus.service.models.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CarbohydrateCalculatorTest {
    private final CarbohydrateCalculator calculator=new CarbohydrateCalculator();

    @Test void calculatesGramBasedProductAndPortionScaling(){var pasta=product("Pasta",new NutritionData(bd("70"),bd("100"),RecipeUnit.GRAM,"test"),List.of());var ingredient=ingredient(pasta,"100",RecipeUnit.GRAM);
        var one=calculator.calculateRecipe(List.of(ingredient),BigDecimal.ONE);var four=calculator.calculateRecipe(List.of(ingredient),bd("4"));
        assertThat(one.totalGrams()).isEqualByComparingTo("70");assertThat(four.totalGrams()).isEqualByComparingTo("280");assertThat(four.perPortionGrams()).isEqualByComparingTo("70");}

    @Test void calculatesMlAndGenericDlTablespoonTeaspoonConversions(){var milk=product("Milk",new NutritionData(bd("4.7"),bd("100"),RecipeUnit.MILLILITER,"test"),List.of());
        var result=calculator.calculateRecipe(List.of(ingredient(milk,"0.5",RecipeUnit.DECILITER),ingredient(milk,"1",RecipeUnit.TABLESPOON),ingredient(milk,"1",RecipeUnit.TEASPOON)),BigDecimal.ONE);
        assertThat(result.totalGrams()).isEqualByComparingTo("3.29");}

    @Test void usesExplicitProductConversionWithoutGuessingDensity(){var flour=product("Flour",new NutritionData(bd("70"),bd("100"),RecipeUnit.GRAM,"test"),List.of(new ProductTemplateUnitConversion(RecipeUnit.TABLESPOON,RecipeUnit.GRAM,bd("9"))));
        assertThat(calculator.calculateRecipe(List.of(ingredient(flour,"2",RecipeUnit.TABLESPOON)),BigDecimal.ONE).totalGrams()).isEqualByComparingTo("12.6");}

    @Test void sumsIngredientsAndDistinguishesKnownZeroFromMissing(){var water=product("Water",new NutritionData(BigDecimal.ZERO,bd("100"),RecipeUnit.MILLILITER,"test"),List.of());var unknown=product("Onion",null,List.of());
        var result=calculator.calculateRecipe(List.of(ingredient(water,"100",RecipeUnit.MILLILITER),ingredient(unknown,"1",RecipeUnit.PIECE)),bd("1.5"));
        assertThat(result.totalGrams()).isEqualByComparingTo("0");assertThat(result.complete()).isFalse();assertThat(result.unknownIngredientCount()).isOne();assertThat(result.ingredients()).extracting(CarbohydrateIngredientResult::known).containsExactly(true,false);}

    @Test void consumedPortionsAreIndependentOfPreparedPortions(){var pasta=product("Pasta",new NutritionData(bd("68"),bd("100"),RecipeUnit.GRAM,"test"),List.of());var prepared=calculator.calculateRecipe(List.of(ingredient(pasta,"100",RecipeUnit.GRAM)),bd("2"));assertThat(prepared.totalGrams()).isEqualByComparingTo("136");assertThat(prepared.carbohydratesForConsumedPortions(BigDecimal.ONE)).isEqualByComparingTo("68");assertThat(prepared.carbohydratesForConsumedPortions(bd("1.5"))).isEqualByComparingTo("102");}

    private RecipeIngredient ingredient(ProductTemplate p,String quantity,RecipeUnit unit){return new RecipeIngredient(UUID.randomUUID(),p,bd(quantity),unit,null,1);}
    private ProductTemplate product(String name,NutritionData nutrition,List<ProductTemplateUnitConversion> conversions){return new ProductTemplate(UUID.randomUUID(),name,ProductCategory.OTHER,Unit.GRAM,InventoryTrackingMode.UNTRACKED,List.of(),false,conversions,nutrition);}
    private BigDecimal bd(String value){return new BigDecimal(value);}
}
