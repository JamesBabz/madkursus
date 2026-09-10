package dk.jamesbabz.madkursus.service.applications;
import dk.jamesbabz.madkursus.service.models.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MealPlanDiscoveryService {
    public static final int DEFAULT_MEAL_COUNT = 5;
    public static final int DEFAULT_PORTIONS = 2;
    private final RecipeMatchingService matching;
    private final IngredientPreferenceResolver resolver;

    public AiChatResponse discover(AiChatIntent intent, Integer maximum) {
        int count = intent.requestedMealCount() == null ? DEFAULT_MEAL_COUNT : intent.requestedMealCount();
        if (count < 1 || count > 14) return new AiChatResponse("Vælg mellem 1 og 14 måltider til din madplan.");
        var preferred = resolve(intent.preferredIngredientTerms());
        var excluded = resolve(intent.excludedIngredientTerms());
        var limited = resolve(intent.limitedIngredientTerms());
        var eligible = matching.findOwnedMatches(maximum, preferred.ids(), excluded.ids(), DEFAULT_PORTIONS);
        var candidates = eligible.stream().limit(Math.min(count + 3, 10)).toList();
        String answer = "Her er " + candidates.size() + " af dine egne opskrifter, du kan vælge imellem til en madplan med "
                + count + " måltider. De er vurderet til 2 portioner efter eksisterende reservationer.";
        if (eligible.size() < count) answer += " Der er ikke nok kendte opskrifter endnu til at fylde hele planen.";
        if (count > 10) answer += " Jeg viser højst 10 kandidater ad gangen.";
        if (!preferred.unresolved().isEmpty()) answer += " Jeg kunne ikke genkende ønskerne: " + String.join(", ", preferred.unresolved()) + ".";
        if (!excluded.unresolved().isEmpty()) answer += " Jeg kunne ikke sikkert udelukke: " + String.join(", ", excluded.unresolved()) + ".";
        if (!intent.limitedIngredientTerms().isEmpty()) answer += " Jeg kunne ikke håndhæve ønsket om mindre " + String.join(", ", intent.limitedIngredientTerms()) + " sikkert endnu.";
        answer += " Kandidaterne er vurderet hver for sig; der er endnu ikke valgt eller oprettet en madplan.";
        return new AiChatResponse(answer, List.of(), new MealPlanProposal(count, DEFAULT_PORTIONS, candidates,
                preferred.unresolved(), excluded.unresolved(), intent.limitedIngredientTerms(), limited.ids(), limited.unresolved()));
    }
    private Resolution resolve(List<String> terms) {
        Set<UUID> ids = new HashSet<>();
        List<String> unresolved = new ArrayList<>();
        for (String term : new LinkedHashSet<>(terms)) {
            var found = resolver.resolve(List.of(term));
            if (found.isEmpty()) unresolved.add(term);
            ids.addAll(found);
        }
        return new Resolution(Set.copyOf(ids), List.copyOf(unresolved));
    }
    private record Resolution(Set<UUID> ids, List<String> unresolved) {}
}
