package dk.jamesbabz.madkursus.service.applications;

import dk.jamesbabz.madkursus.service.models.RecipeMatch;
import org.springframework.stereotype.Component;
import java.util.*;

/** Request-local soft preferences affect ordering, never eligibility or inventory facts. */
@Component
@lombok.extern.slf4j.Slf4j
public class RecipeMatchRanker {
    public List<RecipeMatch> rank(List<RecipeMatch> matches, Set<UUID> preferredTemplateIds) {
        Set<UUID> preferred = Set.copyOf(preferredTemplateIds);
        if (log.isDebugEnabled() && !preferred.isEmpty()) for (var match : matches) {
            var ids = match.ingredientTemplateIds().stream().filter(preferred::contains).sorted().toList();
            log.debug("Recipe preference ranking recipeId={} recipeName={} preferredTemplateMatches={} matchedTemplateIds={} missingIngredients={}",
                    match.id(), match.name(), ids.size(), ids, match.missingIngredientCount());
        }
        return matches.stream().sorted(Comparator.comparingInt((RecipeMatch m) -> Collections.disjoint(m.ingredientTemplateIds(), preferred) ? 1 : 0)
                .thenComparingInt(RecipeMatch::missingIngredientCount)
                .thenComparingInt(m -> m.uncertainIngredients().isEmpty() ? 0 : 1)
                .thenComparingInt(m -> m.uncertainIngredients().size())
                .thenComparing(RecipeMatch::name).thenComparing(m -> m.source().name()).thenComparing(RecipeMatch::id))
                .toList();
    }
}
