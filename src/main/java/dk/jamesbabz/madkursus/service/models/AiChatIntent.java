package dk.jamesbabz.madkursus.service.models;

import java.util.List;

/** Untrusted interpretation of one message, never inventory facts or recipe selections. */
public record AiChatIntent(Intent intent, Boolean inventoryAware,
                           List<String> preferredIngredientTerms, List<String> excludedIngredientTerms, Integer requestedMealCount, List<String> limitedIngredientTerms) {
    public enum Intent { MEAL_DISCOVERY, MEAL_PLAN_DISCOVERY, GENERAL_COOKING, OTHER }
    public AiChatIntent(Intent intent, Boolean inventoryAware, List<String> preferred, List<String> excluded) {
        this(intent, inventoryAware, preferred, excluded, null, List.of());
    }
    public AiChatIntent {
        limitedIngredientTerms = validateTerms(limitedIngredientTerms == null ? List.of() : limitedIngredientTerms);
        if (intent == null || inventoryAware == null) throw new IllegalArgumentException("Missing intent fields");
        preferredIngredientTerms = validateTerms(preferredIngredientTerms);
        excludedIngredientTerms = validateTerms(excludedIngredientTerms);
        if (intent == Intent.MEAL_PLAN_DISCOVERY) {
            // The small contract cannot express a separate hard ban on the same limited term.
            // Do not turn contradictory provider output into an unintended hard exclusion.
            var limited = limitedIngredientTerms.stream().map(AiChatIntent::normalizedTerm).collect(java.util.stream.Collectors.toSet());
            excludedIngredientTerms = excludedIngredientTerms.stream()
                    .filter(term -> !limited.contains(normalizedTerm(term))).toList();
        }
    }
    private static String normalizedTerm(String term) {
        return java.text.Normalizer.normalize(term.strip(), java.text.Normalizer.Form.NFC).toLowerCase(java.util.Locale.ROOT);
    }
    private static List<String> validateTerms(List<String> terms) {
        if (terms == null || terms.size() > 5 || terms.stream().anyMatch(t -> t == null || t.isBlank() || t.length() > 80))
            throw new IllegalArgumentException("Invalid ingredient terms");
        return List.copyOf(terms);
    }
}
