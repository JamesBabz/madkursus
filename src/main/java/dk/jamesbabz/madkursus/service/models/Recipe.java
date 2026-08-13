package dk.jamesbabz.madkursus.service.models;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record Recipe(UUID id, UUID userId, UUID sourceTemplateId, String name, String description, Instant createdAt,
                     Instant updatedAt, List<RecipeIngredient> ingredients, List<RecipeStep> steps) {
 public Recipe(UUID id,UUID userId,String name,String description,Instant createdAt,Instant updatedAt,List<RecipeIngredient> ingredients,List<RecipeStep> steps){this(id,userId,null,name,description,createdAt,updatedAt,ingredients,steps);}
}
