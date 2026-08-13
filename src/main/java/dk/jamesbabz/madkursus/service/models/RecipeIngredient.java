package dk.jamesbabz.madkursus.service.models;
import java.math.BigDecimal;
import java.util.UUID;
public record RecipeIngredient(UUID id, ProductTemplate productTemplate, BigDecimal quantity,
                               RecipeUnit unit, String preparation, int sortOrder) {}
