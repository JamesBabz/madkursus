package dk.jamesbabz.madkursus.service.applications;

import java.util.*;
import java.util.regex.Pattern;

/** Small lexical router: word order/prepositions do not decide discovery intent.
 * Unrecognized content (ingredients, preferences, instructions) stays on the AI path. */
record MealDiscoveryRequest(Integer maximum) {
    private static final Pattern LIMIT = Pattern.compile("højst\\s+([0-9]{1,2})\\s+ekstra\\s+(?:ingrediens(?:er)?|vare(?:r)?)");
    private static final Set<String> DISCOVERY = Set.of("hvad", "kan", "find", "foreslå", "inspiration", "idé", "ide", "idéer", "ideer", "hjælp", "brug");
    private static final Set<String> MEAL = Set.of("lave", "laver", "laves", "spise", "mad", "aftensmad", "opskrift", "opskrifter", "måltid", "måltider", "middag", "middagsmad");
    // Neutral connective/inventory vocabulary, not ingredient names or preference mappings.
    private static final Set<String> NEUTRAL = Set.of("jeg", "vi", "du", "os", "mig", "en", "et", "nogle", "noget", "det", "der", "er", "har", "have", "mit", "min", "mine", "vores", "på", "i", "med", "af", "fra", "til", "og", "som", "at", "så", "ud", "for", "gerne", "venligst", "lige", "nu", "aften", "dag", "idag", "hjemme", "lager", "inventar", "køleskab", "råvarer", "ingredienser", "kun", "allerede", "hvis", "køber", "købe", "vil");
    static Optional<MealDiscoveryRequest> from(String message, Integer maximum) {
        String text = message.toLowerCase(Locale.forLanguageTag("da")).strip();
        var limit = LIMIT.matcher(text);
        if (limit.find()) {
            int count = Integer.parseInt(limit.group(1));
            if (count > 20) return Optional.empty();
            maximum = maximum == null ? count : Math.min(count, maximum);
            text = limit.replaceFirst(" ");
        }
        var tokens = Arrays.stream(text.split("[^\\p{L}\\p{N}]+" )).filter(t -> !t.isBlank()).toList();
        if (tokens.isEmpty() || tokens.stream().anyMatch(t -> !NEUTRAL.contains(t) && !DISCOVERY.contains(t) && !MEAL.contains(t))) return Optional.empty();
        boolean stockOnly = tokens.contains("kun") && (tokens.contains("har") || tokens.contains("lager") || tokens.contains("inventar"));
        boolean discovery = tokens.stream().anyMatch(DISCOVERY::contains);
        boolean meal = tokens.stream().anyMatch(MEAL::contains);
        if (!(discovery && (meal || stockOnly)) && !(maximum != null && tokens.contains("købe"))) return Optional.empty();
        return Optional.of(new MealDiscoveryRequest(stockOnly ? Integer.valueOf(0) : maximum));
    }
}
