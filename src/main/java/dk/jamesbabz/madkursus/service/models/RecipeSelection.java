package dk.jamesbabz.madkursus.service.models;
import java.math.BigDecimal; import java.util.UUID;
public record RecipeSelection(UUID recipeId, BigDecimal portions) {}
