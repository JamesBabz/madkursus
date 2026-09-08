package dk.jamesbabz.madkursus.service.models;

import java.util.List;

/** Untrusted interpretation of one message, never inventory facts or recipe selections. */
public record AiChatIntent(Intent intent, Boolean inventoryAware,
                           List<String> preferredIngredientTerms, List<String> excludedIngredientTerms) {
    public enum Intent { MEAL_DISCOVERY, GENERAL_COOKING, OTHER }
    public AiChatIntent {
        if (intent == null || inventoryAware == null) throw new IllegalArgumentException("Missing intent fields");
        preferredIngredientTerms = validateTerms(preferredIngredientTerms);
        excludedIngredientTerms = validateTerms(excludedIngredientTerms);
    }
    private static List<String> validateTerms(List<String> terms) {
        if (terms == null || terms.size() > 5 || terms.stream().anyMatch(t -> t == null || t.isBlank() || t.length() > 80))
            throw new IllegalArgumentException("Invalid ingredient terms");
        return List.copyOf(terms);
    }
}
