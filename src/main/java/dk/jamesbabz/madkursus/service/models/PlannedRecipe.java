package dk.jamesbabz.madkursus.service.models;
import java.util.UUID;
public record PlannedRecipe(UUID id, Recipe recipe, String recipeName, int portions, int sortOrder, PlannedRecipeStatus status) {
    public PlannedRecipe(UUID id,Recipe recipe,int portions,int sortOrder,PlannedRecipeStatus status) {
        this(id,recipe,recipe.name(),portions,sortOrder,status);
    }
}
