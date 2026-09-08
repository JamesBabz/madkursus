package dk.jamesbabz.madkursus.service.applications;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class IngredientPreferenceResolver {
    private final ProductTemplateService templates;
    public Set<UUID> resolve(List<String> terms) {
        Set<UUID> resolved = new HashSet<>();
        for (String term : new LinkedHashSet<>(terms)) {
            var candidates = templates.resolveDiscoveryTerm(term);
            var ids = candidates.stream().map(t -> t.id()).distinct().toList();
            resolved.addAll(ids);
            log.debug("AI preference resolution preferredTerm={} candidateTemplates={} resolvedTemplateIds={}", term,
                    candidates.stream().map(t -> t.id() + ":" + t.name()).toList(), ids);
        }
        return Set.copyOf(resolved);
    }
}
