package dk.jamesbabz.madkursus.service.applications;

import dk.jamesbabz.madkursus.service.exceptions.AiUnavailableException;
import dk.jamesbabz.madkursus.service.models.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

@lombok.extern.slf4j.Slf4j
@Component
public class AiSuggestionValidator {
    /** Only references offered in this request can be resolved. Never resolve by model-generated names. */
    public record Candidate(String reference, String name, UUID productId, UUID templateId,
                            ProductCategory category, Unit unit, InventoryTrackingMode mode) {}
    public enum Status { OWNED, OWNED_UNQUANTIFIED, SHORTAGE, MISSING, UNKNOWN_AMOUNT }
    public record CheckedIngredient(String name, BigDecimal usage, Unit unit, BigDecimal stock,
                                    BigDecimal shortage, Status status) {}
    public record CheckedSuggestion(String name, List<CheckedIngredient> ingredients, int additionalIngredients) {}
    public enum Reason { INVALID_STRUCTURED_RESPONSE, UNKNOWN_REFERENCE, AMBIGUOUS_REFERENCE, INVALID_QUANTITY, UNIT_MISMATCH, UNTRACKED_INGREDIENT, INVALID_STOCK, MAX_ADDITIONAL_INGREDIENTS_EXCEEDED }
    public record Rejection(int suggestionIndex, Reason reason, String reference) {}
    public record Result(List<CheckedSuggestion> suggestions, List<Rejection> rejections) {
        public int withheld() { return rejections.size(); }
    }
    private static class Rejected extends RuntimeException {
        final Reason reason; final String reference;
        Rejected(Reason reason, String reference) { this.reason = reason; this.reference = reference; }
    }
    private static Rejected reject(Reason reason, String reference) { return new Rejected(reason, reference); }

    private static AiUnavailableException invalidEnvelope() {
        log.warn("AI validation failed reason=INVALID_STRUCTURED_RESPONSE");
        return new AiUnavailableException();
    }

    public List<Candidate> candidates(List<InventoryItem> inventory, List<ProductTemplate> templates) {
        List<Candidate> result = new ArrayList<>();
        for (InventoryItem item : inventory) {
            Product p = item.product();
            result.add(new Candidate("product:" + p.id(), p.name(), p.id(), p.sourceTemplateId(), p.category(), p.defaultUnit(), p.inventoryTrackingMode()));
        }
        for (ProductTemplate t : templates) result.add(new Candidate("template:" + t.id(), t.name(), null, t.id(), t.category(), t.defaultUnit(), t.defaultTrackingMode()));
        return List.copyOf(result);
    }

    public Result validate(AiMealProposal proposal, List<Candidate> candidates, List<InventoryItem> inventory, Integer maximum) {
        if (proposal == null || proposal.reply() == null || proposal.suggestions() == null || proposal.suggestions().size() > 3) throw invalidEnvelope();
        if (proposal.reply() != AiMealProposal.Reply.SUGGESTIONS) {
            if (!proposal.suggestions().isEmpty()) throw invalidEnvelope();
            return new Result(List.of(), List.of());
        }
        Map<String, Candidate> allowed = new HashMap<>();
        candidates.forEach(c -> allowed.put(c.reference(), c));
        List<CheckedSuggestion> checked = new ArrayList<>();
        List<Rejection> rejections = new ArrayList<>();
        for (int index = 0; index < proposal.suggestions().size(); index++) {
            try {
                var result = check(proposal.suggestions().get(index), allowed, inventory);
                if (maximum != null && result.additionalIngredients() > maximum) throw reject(Reason.MAX_ADDITIONAL_INGREDIENTS_EXCEEDED, null);
                checked.add(result);
                log.debug("AI suggestion accepted index={} additionalIngredients={}", index, result.additionalIngredients());
            } catch (Rejected rejection) {
                rejections.add(new Rejection(index, rejection.reason, rejection.reference));
                log.debug("AI suggestion rejected index={} reason={} reference={}", index, rejection.reason, rejection.reference);
            }
        }
        if (!rejections.isEmpty()) log.warn("AI validation accepted={} rejected={} reasons={}", checked.size(), rejections.size(), rejections.stream().map(Rejection::reason).distinct().toList());
        return new Result(List.copyOf(checked), List.copyOf(rejections));
    }

    private CheckedSuggestion check(AiMealProposal.Suggestion suggestion, Map<String, Candidate> allowed, List<InventoryItem> inventory) {
        if (suggestion == null || suggestion.name() == null || suggestion.name().isBlank() || suggestion.name().length() > 120
                || suggestion.name().contains("\n") || suggestion.ingredients() == null || suggestion.ingredients().isEmpty() || suggestion.ingredients().size() > 20) throw reject(Reason.INVALID_STRUCTURED_RESPONSE, null);
        Map<String, Candidate> canonical = new LinkedHashMap<>();
        Map<String, BigDecimal> quantities = new HashMap<>();
        Map<String, Boolean> quantified = new HashMap<>();
        Map<String, InventoryItem> stockItems = new HashMap<>();
        for (var ingredient : suggestion.ingredients()) {
            if (ingredient == null) throw reject(Reason.INVALID_STRUCTURED_RESPONSE, null);
            Candidate c = allowed.get(ingredient.reference());
            if (c == null) throw reject(Reason.UNKNOWN_REFERENCE, ingredient.reference());
            if (c.mode() == InventoryTrackingMode.UNTRACKED) throw reject(Reason.UNTRACKED_INGREDIENT, c.reference());
            if (ingredient.unit() != null && ingredient.unit() != c.unit()) throw reject(Reason.UNIT_MISMATCH, c.reference());
            BigDecimal amount = ingredient.quantity();
            if (amount != null && (amount.signum() <= 0 || amount.precision() > 12 || Math.abs((long) amount.scale()) > 6)) throw reject(Reason.INVALID_QUANTITY, c.reference());
            if (amount != null && ingredient.unit() == null) throw reject(Reason.UNIT_MISMATCH, c.reference());
            List<InventoryItem> matches = inventory.stream().filter(i ->
                    (c.productId() != null && c.productId().equals(i.product().id()))
                    || (c.templateId() != null && c.templateId().equals(i.product().sourceTemplateId()))).toList();
            if (matches.size() > 1) throw reject(Reason.AMBIGUOUS_REFERENCE, c.reference());
            InventoryItem stock = matches.isEmpty() ? null : matches.getFirst();
            // Same category is NOT equivalence. It is a reason to withhold an ambiguous absence claim.
            if (stock == null && inventory.stream().anyMatch(i -> i.product().category() == c.category())) throw reject(Reason.AMBIGUOUS_REFERENCE, c.reference());
            if (stock != null && stock.unit() != c.unit()) throw reject(Reason.UNIT_MISMATCH, c.reference());
            if (stock != null && stock.product().inventoryTrackingMode() == InventoryTrackingMode.UNTRACKED) throw reject(Reason.UNTRACKED_INGREDIENT, c.reference());
            String key = stock != null ? "product:" + stock.product().id() : c.reference();
            log.debug("AI reference resolved reference={} productId={} templateId={} stockProductId={}", c.reference(), c.productId(), c.templateId(), stock == null ? null : stock.product().id());
            Boolean previous = quantified.putIfAbsent(key, amount != null);
            if (previous != null && previous != (amount != null)) throw reject(Reason.INVALID_QUANTITY, c.reference());
            canonical.putIfAbsent(key, c);
            if (stock != null) stockItems.put(key, stock);
            if (amount != null) quantities.merge(key, amount, BigDecimal::add);
        }
        List<CheckedIngredient> ingredients = new ArrayList<>();
        int additional = 0;
        for (var entry : canonical.entrySet()) {
            Candidate c = entry.getValue(); InventoryItem stock = stockItems.get(entry.getKey());
            String name = stock == null ? c.name() : stock.product().name();
            BigDecimal usage = quantities.get(entry.getKey());
            if (stock == null) {
                additional++;
                ingredients.add(new CheckedIngredient(name, usage, c.unit(), BigDecimal.ZERO, usage, Status.MISSING));
            } else if (stock.product().inventoryTrackingMode() == InventoryTrackingMode.PRESENCE) {
                ingredients.add(new CheckedIngredient(name, usage, c.unit(), null, null, Status.UNKNOWN_AMOUNT));
            } else {
                if (stock.quantity() == null || stock.quantity().signum() < 0) throw reject(Reason.INVALID_STOCK, c.reference());
                if (usage == null) {
                    boolean empty = stock.quantity().signum() == 0;
                    if (empty) additional++;
                    ingredients.add(new CheckedIngredient(name, null, c.unit(), stock.quantity(), null, empty ? Status.MISSING : Status.OWNED_UNQUANTIFIED));
                    continue;
                }
                BigDecimal shortage = usage.subtract(stock.quantity()).max(BigDecimal.ZERO);
                if (shortage.signum() > 0) additional++;
                ingredients.add(new CheckedIngredient(name, usage, c.unit(), stock.quantity(), shortage,
                        shortage.signum() == 0 ? Status.OWNED : stock.quantity().signum() == 0 ? Status.MISSING : Status.SHORTAGE));
            }
        }
        return new CheckedSuggestion(suggestion.name(), List.copyOf(ingredients), additional);
    }

    public AiChatResponse present(AiMealProposal proposal, Result result, Integer maximum) {
        if (proposal.reply() == AiMealProposal.Reply.OUT_OF_SCOPE) return new AiChatResponse("Jeg er Madkursus' køkkenhjælper. Jeg hjælper med mad, råvarer og indkøb til madlavning. Skal vi finde en idé til aftensmaden?");
        if (proposal.reply() == AiMealProposal.Reply.NEED_DISH) return new AiChatResponse("Nævn gerne retten og dine ønsker i spørgsmålet. Jeg kan ikke se tidligere beskeder. Lige nu kan jeg hjælpe med måltidsidéer og kontrollere deres ingredienser mod dit lager.");
        if (result.suggestions().isEmpty()) return new AiChatResponse("Jeg fandt ikke et forslag, hvor jeg kunne kontrollere ingredienserne" + (maximum == null ? "." : " inden for din grænse på " + maximum + " ekstra ingredienser.") + " Prøv at nævne konkrete råvarer eller en ret.");
        StringBuilder answer = new StringBuilder("Her er et par måltidsidéer:\n");
        for (var suggestion : result.suggestions()) {
            answer.append("\n").append(suggestion.name()).append("\n");
            var missing = suggestion.ingredients().stream().filter(i -> i.status() == Status.MISSING || i.status() == Status.SHORTAGE).toList();
            if (missing.isEmpty()) answer.append("Du har allerede ingredienserne.\n");
            else {
                answer.append("Du mangler:\n");
                for (var ingredient : missing) {
                    answer.append("- ").append(ingredient.name());
                    if (ingredient.shortage() != null) answer.append(": ").append(QuantityDisplay.format(ingredient.shortage(), ingredient.unit()));
                    answer.append("\n");
                }
            }
        }
        return new AiChatResponse(answer.toString().strip());
    }
}
