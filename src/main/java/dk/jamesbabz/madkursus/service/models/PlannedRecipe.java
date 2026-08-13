package dk.jamesbabz.madkursus.service.models;
import java.util.UUID;
public record PlannedRecipe(UUID id, Recipe recipe, int portions, int sortOrder, PlannedRecipeStatus status) {}
