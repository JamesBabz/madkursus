package dk.jamesbabz.madkursus.service.models;
import java.util.List;
public record AiChatResponse(String answer, List<RecipeMatch> knownRecipes) {
    public AiChatResponse(String answer) { this(answer, List.of()); }
}
