package dk.jamesbabz.madkursus.service.models;
import java.math.BigDecimal;import java.util.UUID;
public record PreparedComponentIngredient(UUID id,UUID recipeIngredientId,ProductTemplate productTemplate,BigDecimal quantity,RecipeUnit unit,int sortOrder) {}
