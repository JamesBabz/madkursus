package dk.jamesbabz.madkursus.service.models;
import java.util.List;
import java.util.Set;
import java.util.UUID;
/** Read-only candidates; limited terms are transported but not enforced in Phase 2. */
public record MealPlanProposal(int requestedMealCount, int defaultPortions, List<RecipeMatch> candidates,
        List<String> unresolvedPreferredIngredientTerms, List<String> unresolvedExcludedIngredientTerms,
        List<String> limitedIngredientTerms, Set<UUID> limitedIngredientTemplateIds,
        List<String> unresolvedLimitedIngredientTerms) {}
