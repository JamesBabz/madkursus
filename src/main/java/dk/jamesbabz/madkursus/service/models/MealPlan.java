package dk.jamesbabz.madkursus.service.models;
import java.time.Instant; import java.util.List; import java.util.UUID;
public record MealPlan(UUID id, UUID userId, String name, Instant createdAt, Instant updatedAt, List<PlannedRecipe> recipes) { public boolean completed(){return recipes.stream().noneMatch(r->r.status()==PlannedRecipeStatus.PLANNED);} }
