package dk.jamesbabz.madkursus.service.ports;
import java.util.*; import dk.jamesbabz.madkursus.service.models.MealPlan;
public interface MealPlanPort { MealPlan save(MealPlan plan); Optional<MealPlan> findByIdAndUserId(UUID id,UUID userId); List<MealPlan> findAllByUserId(UUID userId); boolean existsPlannedByRecipeIdAndUserId(UUID recipeId,UUID userId); void detachHistoricalRecipeReferences(UUID recipeId,UUID userId); void deleteByIdAndUserId(UUID id,UUID userId); }
