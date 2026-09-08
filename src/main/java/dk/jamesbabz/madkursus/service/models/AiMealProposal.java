package dk.jamesbabz.madkursus.service.models;

import java.math.BigDecimal;
import java.util.List;

/** Untrusted meal ideas, never recipes or authoritative inventory facts. */
public record AiMealProposal(Reply reply, List<Suggestion> suggestions) {
    public enum Reply {
        SUGGESTIONS("Use whenever proposing meals, including meals under an additional-ingredient limit."),
        NEED_DISH("Use only for clarification when a previous dish is unnamed in this stateless request, or for cooking instructions beyond this meal-idea feature. Never include meal suggestions."),
        NO_SUGGESTIONS("Use when no suitable meal can be proposed; do not invent weak meals."),
        OUT_OF_SCOPE("Use for requests unrelated to cooking; never answer the unrelated request.");

        private final String instruction;
        Reply(String instruction) { this.instruction = instruction; }
        public boolean allowsSuggestions() { return this == SUGGESTIONS; }
        public String promptInstruction() {
            return name() + ": " + instruction + (allowsSuggestions() ? "" : " suggestions must be [].");
        }
    }
    public record Suggestion(String name, List<Ingredient> ingredients) {}
    public record Ingredient(String reference, BigDecimal quantity, Unit unit) {}
}
