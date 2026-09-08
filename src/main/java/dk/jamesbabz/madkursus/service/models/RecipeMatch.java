package dk.jamesbabz.madkursus.service.models;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A match for the requested portions against physical stock, independent of chat or AI. */
public record RecipeMatch(UUID id, Source source, String name, State state,
                          List<MissingIngredient> missingIngredients, List<String> uncertainIngredients,
                          java.util.Set<UUID> ingredientTemplateIds) {
    public RecipeMatch {
        ingredientTemplateIds = java.util.Set.copyOf(ingredientTemplateIds);
    }
    public RecipeMatch(UUID id, Source source, String name, State state,
                       List<MissingIngredient> missingIngredients, List<String> uncertainIngredients) {
        this(id, source, name, state, missingIngredients, uncertainIngredients, java.util.Set.of());
    }
    public enum Source { RECIPE, TEMPLATE }
    public enum State { COOKABLE, NEAR_MATCH, CHECK_QUANTITIES, UNRESOLVED }
    public record MissingIngredient(UUID productTemplateId, String name, BigDecimal shortage, Unit unit) {}
    public int missingIngredientCount() { return missingIngredients.size(); }
}
