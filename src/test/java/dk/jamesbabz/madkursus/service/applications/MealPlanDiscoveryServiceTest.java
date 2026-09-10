package dk.jamesbabz.madkursus.service.applications;
import dk.jamesbabz.madkursus.service.models.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MealPlanDiscoveryServiceTest {
    final RecipeMatchingService matching = mock(RecipeMatchingService.class);
    final ProductTemplateService templates = mock(ProductTemplateService.class);
    final MealPlanDiscoveryService service = new MealPlanDiscoveryService(matching, new IngredientPreferenceResolver(templates));
    AiChatIntent intent(Integer count) { return new AiChatIntent(AiChatIntent.Intent.MEAL_PLAN_DISCOVERY, true, List.of(), List.of(), count, List.of()); }
    List<RecipeMatch> candidates(int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> new RecipeMatch(UUID.randomUUID(), RecipeMatch.Source.RECIPE,
            "Dinner " + i, RecipeMatch.State.COOKABLE, List.of(), List.of(), Set.of())).toList();
    }
    @Test void boundedChoicesAndJavaDefaultUseTwoPortions() {
        when(matching.findOwnedMatches(null, Set.of(), Set.of(), 2)).thenReturn(candidates(20));
        for (Integer count : Arrays.asList(null, 1, 5, 7, 10, 14)) {
            var plan = service.discover(intent(count), null).mealPlanProposal();
            assertThat(plan.requestedMealCount()).isEqualTo(count == null ? 5 : count);
            assertThat(plan.defaultPortions()).isEqualTo(2);
            assertThat(plan.candidates()).hasSize(Math.min((count == null ? 5 : count) + 3, 10));
        }
    }
    @Test void candidateCapDoesNotPretendThereAreTooFewOwnedRecipes() {
        when(matching.findOwnedMatches(null, Set.of(), Set.of(), 2)).thenReturn(candidates(20));
        var result = service.discover(intent(14), null);
        assertThat(result.mealPlanProposal().candidates()).hasSize(10);
        assertThat(result.answer()).contains("højst 10").doesNotContain("ikke nok kendte");
    }
    @Test void shortageReturnsThreeRealCandidates() {
        var recipes = candidates(3);
        when(matching.findOwnedMatches(null, Set.of(), Set.of(), 2)).thenReturn(recipes);
        var result = service.discover(intent(5), null);
        assertThat(result.mealPlanProposal().candidates()).isEqualTo(recipes);
        assertThat(result.answer()).contains("3 af dine egne", "5 måltider", "ikke nok kendte opskrifter");
    }
    @Test void resolvesEveryReviewedIdentityAndReportsUnsupportedTerms() {
        var chicken = new ProductTemplate(UUID.randomUUID(), "Chicken", ProductCategory.OTHER, Unit.GRAM, InventoryTrackingMode.QUANTITY, List.of(), true);
        var otherChicken = new ProductTemplate(UUID.randomUUID(), "Other chicken", ProductCategory.OTHER, Unit.GRAM, InventoryTrackingMode.QUANTITY, List.of(), true);
        var egg = new ProductTemplate(UUID.randomUUID(), "Egg", ProductCategory.OTHER, Unit.PIECE, InventoryTrackingMode.QUANTITY, List.of(), true);
        when(templates.resolveDiscoveryTerm("kylling")).thenReturn(List.of(chicken, otherChicken));
        when(templates.resolveDiscoveryTerm("æg")).thenReturn(List.of(egg));
        var result = service.discover(new AiChatIntent(AiChatIntent.Intent.MEAL_PLAN_DISCOVERY, true,
                List.of("kylling", "unknown preference"), List.of("æg", "unknown exclusion"), 5, List.of("pasta")), 1);
        verify(matching).findOwnedMatches(1, Set.of(chicken.id(), otherChicken.id()), Set.of(egg.id()), 2);
        var plan = result.mealPlanProposal();
        assertThat(plan.unresolvedPreferredIngredientTerms()).containsExactly("unknown preference");
        assertThat(plan.unresolvedExcludedIngredientTerms()).containsExactly("unknown exclusion");
        assertThat(plan.limitedIngredientTerms()).containsExactly("pasta");
        assertThat(plan.unresolvedLimitedIngredientTerms()).containsExactly("pasta");
        assertThat(result.answer()).contains("ikke håndhæve", "pasta", "ikke sikkert udelukke");
    }
    @Test void contradictoryUnresolvedPastaProducesOnlyLimitedWarning() {
        var intent = new AiChatIntent(AiChatIntent.Intent.MEAL_PLAN_DISCOVERY, false, List.of(), List.of(" PASTA "), 5, List.of("pasta"));
        var result = service.discover(intent, null);
        assertThat(result.mealPlanProposal().unresolvedExcludedIngredientTerms()).isEmpty();
        assertThat(result.mealPlanProposal().unresolvedLimitedIngredientTerms()).containsExactly("pasta");
        assertThat(result.answer()).contains("mindre pasta").doesNotContain("udelukke");
        verify(matching).findOwnedMatches(null, Set.of(), Set.of(), 2);
    }
    @Test void contradictoryResolvedTermDoesNotBecomeHardExclusion() {
        var template = new ProductTemplate(UUID.randomUUID(), "Concrete", ProductCategory.OTHER, Unit.GRAM, InventoryTrackingMode.QUANTITY, List.of(), true);
        when(templates.resolveDiscoveryTerm("concrete")).thenReturn(List.of(template));
        var intent = new AiChatIntent(AiChatIntent.Intent.MEAL_PLAN_DISCOVERY, false, List.of(), List.of("CONCRETE"), 5, List.of("concrete"));
        var result = service.discover(intent, null);
        assertThat(result.mealPlanProposal().limitedIngredientTemplateIds()).containsExactly(template.id());
        verify(matching).findOwnedMatches(null, Set.of(), Set.of(), 2);
    }
    @Test void resolvedLimitedTermsAreTransportedButNeverExcluded() {
        var id = UUID.randomUUID();
        when(templates.resolveDiscoveryTerm("concrete ingredient")).thenReturn(List.of(new ProductTemplate(id, "Concrete", ProductCategory.OTHER, Unit.GRAM, InventoryTrackingMode.QUANTITY, List.of(), true)));
        var result = service.discover(new AiChatIntent(AiChatIntent.Intent.MEAL_PLAN_DISCOVERY, false, List.of(), List.of(), 5, List.of("concrete ingredient")), null);
        assertThat(result.mealPlanProposal().limitedIngredientTemplateIds()).containsExactly(id);
        assertThat(result.mealPlanProposal().unresolvedLimitedIngredientTerms()).isEmpty();
        assertThat(result.answer()).contains("ikke håndhæve");
        verify(matching).findOwnedMatches(null, Set.of(), Set.of(), 2);
    }
}
