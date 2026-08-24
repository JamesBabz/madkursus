package dk.jamesbabz.madkursus.service.models;
import java.math.BigDecimal;import java.util.UUID;
public record RecipeInstructionPart(String text,UUID recipeIngredientId,BigDecimal quantity,RecipeUnit unit,UUID preparedComponentId,BigDecimal scaledNumber) {}
