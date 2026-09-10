package dk.jamesbabz.madkursus.service.models;
import java.util.List;
public record AiChatResponse(String answer, List<RecipeMatch> knownRecipes, MealPlanProposal mealPlanProposal) {
    public AiChatResponse(String answer) { this(answer, List.of(), null); }
    public AiChatResponse(String answer, List<RecipeMatch> knownRecipes) { this(answer, knownRecipes, null); }
}
