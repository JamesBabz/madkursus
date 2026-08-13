package dk.jamesbabz.madkursus.service.models;
import java.math.BigDecimal; import java.util.List;
public record RecipeCookResult(Recipe recipe, BigDecimal portions, RecipeCookHistory history,
 List<RecipeRequirement> deductions, List<String> warnings) {}
