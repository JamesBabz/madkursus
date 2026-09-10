package dk.jamesbabz.madkursus.service.applications;

import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.ports.*;
import dk.jamesbabz.madkursus.service.exceptions.InvalidInputException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecipeMatchingService {
    private final CurrentUserProvider currentUser;
    private final InventoryAvailabilityService availabilityService;
    private final RecipePort recipes;
    private final RecipeTemplatePort templates;
    private final RecipeQuantityNormalizer normalizer;
    private final RecipeMatchRanker ranker;

    /** Loads one user-scoped snapshot. No rendering, mutations, AI or per-recipe database lookups. */
    public List<RecipeMatch> findMatches(Integer maximum) {
        return findMatches(maximum, Set.of());
    }

    public List<RecipeMatch> findMatches(Integer maximum, Set<UUID> preferredTemplateIds) {
        return findMatches(maximum, preferredTemplateIds, 1);
    }

    public List<RecipeMatch> findMatches(Integer maximum, Set<UUID> preferredTemplateIds, int portions) {
        return findMatches(maximum, preferredTemplateIds, portions, false, Set.of());
    }
    public List<RecipeMatch> findOwnedMatches(Integer maximum, Set<UUID> preferredTemplateIds, Set<UUID> excludedTemplateIds, int portions) {
        return findMatches(maximum, preferredTemplateIds, portions, true, excludedTemplateIds);
    }
    private List<RecipeMatch> findMatches(Integer maximum, Set<UUID> preferredTemplateIds, int portions,
                                        boolean ownedOnly, Set<UUID> excludedTemplateIds) {
        if (portions < 1) throw new InvalidInputException("Portions must be positive");
        if (maximum != null && (maximum < 0 || maximum > 20)) throw new InvalidInputException("Maximum additional ingredients must be between 0 and 20");
        UUID user = currentUser.currentUserId();
        var stock = availabilityService.snapshot(null);
        var own = recipes.findAllByUserId(user).stream().filter(r -> user.equals(r.userId())).toList();
        var copied = own.stream().map(Recipe::sourceTemplateId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<RecipeMatch> matches = new ArrayList<>();
        for (var recipe : own) {
            if (recipe.ingredients().stream().anyMatch(i -> i.productTemplate() != null && i.productTemplate().id() != null && excludedTemplateIds.contains(i.productTemplate().id()))) continue;
            matches.add(match(recipe.id(), RecipeMatch.Source.RECIPE, recipe.name(), recipe.ingredients(), stock, portions));
        }
        for (var template : ownedOnly ? List.<RecipeTemplate>of() : templates.search(null)) {
            if (!template.active() || copied.contains(template.id())) continue;
            var ingredients = template.ingredients().stream().map(i -> new RecipeIngredient(i.id(), i.productTemplate(), i.quantity(), i.unit(), i.preparation(), i.sortOrder())).toList();
            matches.add(match(template.id(), RecipeMatch.Source.TEMPLATE, template.name(), ingredients, stock, portions));
        }
        var eligible = matches.stream().filter(m -> m.state() != RecipeMatch.State.UNRESOLVED)
                .filter(m -> maximum == null || (maximum == 0 ? m.state() == RecipeMatch.State.COOKABLE : m.missingIngredientCount() <= maximum))
                .toList();
        return ranker.rank(eligible, preferredTemplateIds);
    }

    private RecipeMatch match(UUID id, RecipeMatch.Source source, String name, List<RecipeIngredient> ingredients, InventoryAvailabilityService.Snapshot inventory, int portions) {
        Map<UUID, ProductTemplate> identities = new LinkedHashMap<>();
        Map<UUID, BigDecimal> required = new HashMap<>();
        List<String> uncertain = new ArrayList<>();
        List<RecipeMatch.MissingIngredient> missing = new ArrayList<>();
        boolean unresolved = ingredients.isEmpty();
        for (var ingredient : ingredients) {
            var template = ingredient.productTemplate();
            if (template == null || template.id() == null || ingredient.quantity() == null || ingredient.quantity().signum() <= 0 || ingredient.unit() == null) {
                unresolved = true; continue;
            }
            identities.putIfAbsent(template.id(), template);
            // Presence does not require inventing a conversion for a quantity we cannot measure.
            boolean quantityUnknown = availabilityService.forTemplate(inventory, template).trackingMode() != InventoryTrackingMode.QUANTITY;
            if (quantityUnknown) { required.putIfAbsent(template.id(), BigDecimal.ZERO); continue; }
            var amount = normalizer.normalize(ingredient.quantity().multiply(BigDecimal.valueOf(portions)), ingredient.unit(), template);
            if (amount.warning() != null) { unresolved = true; continue; }
            required.merge(template.id(), amount.quantity(), BigDecimal::add);
        }
        for (var template : identities.values()) {
            var stock = availabilityService.forTemplate(inventory, template);
            if (!required.containsKey(template.id())) continue;
            if (stock.warning() != null) { uncertain.add(template.name()); continue; }
            if (stock.trackingMode() == InventoryTrackingMode.UNTRACKED) continue;
            if (stock.trackingMode() == InventoryTrackingMode.PRESENCE) {
                if (stock.product() == null) missing.add(new RecipeMatch.MissingIngredient(template.id(), template.name(), null, template.defaultUnit()));
                else uncertain.add(template.name());
                continue;
            }
            BigDecimal available = stock.availableQuantity();
            BigDecimal shortage = required.get(template.id()).subtract(available).max(BigDecimal.ZERO);
            if (shortage.signum() > 0) missing.add(new RecipeMatch.MissingIngredient(template.id(), template.name(), QuantityRoundingPolicy.forInventory(shortage, template.defaultUnit()), template.defaultUnit()));
        }
        var state = unresolved ? RecipeMatch.State.UNRESOLVED : !uncertain.isEmpty() ? RecipeMatch.State.CHECK_QUANTITIES : missing.isEmpty() ? RecipeMatch.State.COOKABLE : RecipeMatch.State.NEAR_MATCH;
        return new RecipeMatch(id, source, name, state, List.copyOf(missing), List.copyOf(uncertain), identities.keySet());
    }
}
