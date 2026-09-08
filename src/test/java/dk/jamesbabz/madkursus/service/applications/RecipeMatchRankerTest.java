package dk.jamesbabz.madkursus.service.applications;

import dk.jamesbabz.madkursus.service.models.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class RecipeMatchRankerTest {
    final RecipeMatchRanker ranker = new RecipeMatchRanker();
    final UUID chicken = UUID.randomUUID(), beef = UUID.randomUUID();
    RecipeMatch match(String name, UUID ingredient, int missing, boolean uncertain) {
        var shortages = java.util.stream.IntStream.range(0,missing).mapToObj(i -> new RecipeMatch.MissingIngredient(UUID.randomUUID(),"Missing"+i,null,Unit.GRAM)).toList();
        return new RecipeMatch(UUID.randomUUID(),RecipeMatch.Source.RECIPE,name,
                uncertain ? RecipeMatch.State.CHECK_QUANTITIES : missing == 0 ? RecipeMatch.State.COOKABLE : RecipeMatch.State.NEAR_MATCH,
                shortages, uncertain ? List.of("Salt") : List.of(), Set.of(ingredient));
    }
    @Test void softPreferenceBoostsEqualMatchesWithoutFilteringOtherRecipes() {
        var a=match("A beef",beef,0,false);var z=match("Z chicken",chicken,0,false);
        assertThat(ranker.rank(List.of(a,z),Set.of(chicken))).containsExactly(z,a);
        assertThat(ranker.rank(List.of(a,z),Set.of())).containsExactly(a,z);
        assertThat(ranker.rank(List.of(a,z),Set.of(UUID.randomUUID()))).containsExactly(a,z);
    }
    @Test void preferenceTakesPriorityAndAvailabilityOrdersWithinPreference() {
        var exact=match("Beef",beef,0,false);var uncertain=match("Chicken",chicken,0,true);
        var near=match("Chicken near",chicken,1,false);var distant=match("Chicken distant",chicken,2,false);
        assertThat(ranker.rank(List.of(distant,near,uncertain,exact),Set.of(chicken))).containsExactly(uncertain,near,distant,exact);
        assertThat(ranker.rank(List.of(distant,near,uncertain,exact),Set.of())).containsExactly(exact,uncertain,near,distant);
    }
    @Test void preferencesAlsoOrderNearMatchesWithoutChangingTheirShortages() {
        var beefMeal=match("A beef",beef,1,false);var chickenMeal=match("Z chicken",chicken,1,false);
        assertThat(ranker.rank(List.of(beefMeal,chickenMeal),Set.of(chicken))).containsExactly(chickenMeal,beefMeal);
        assertThat(chickenMeal.missingIngredientCount()).isEqualTo(1);
    }
    @Test void equalNamesAndPreferenceScoresUseStableIdentityTieBreaks() {
        var original=match("Meal",chicken,0,false);
        var low=new RecipeMatch(new UUID(0,1),original.source(),original.name(),original.state(),original.missingIngredients(),original.uncertainIngredients(),original.ingredientTemplateIds());
        var high=new RecipeMatch(new UUID(0,2),original.source(),original.name(),original.state(),original.missingIngredients(),original.uncertainIngredients(),original.ingredientTemplateIds());
        assertThat(ranker.rank(List.of(high,low),Set.of(chicken))).containsExactly(low,high);
        assertThat(ranker.rank(List.of(low,high),Set.of(chicken))).containsExactly(low,high);
    }
    @Test void requestedChickenExampleRanksNearChickenBeforeFullyOwnedBeef() {
        var frikadeller=match("Frikadeller",beef,0,false);
        var curry=match("Kylling i karry",chicken,1,false);var rice=match("Kylling med ris",chicken,2,false);
        assertThat(ranker.rank(List.of(frikadeller,rice,curry),Set.of(chicken))).containsExactly(curry,rice,frikadeller);
        assertThat(ranker.rank(List.of(rice,curry,frikadeller),Set.of())).containsExactly(frikadeller,curry,rice);
    }
}
