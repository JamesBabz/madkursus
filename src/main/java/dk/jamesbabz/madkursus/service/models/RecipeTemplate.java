package dk.jamesbabz.madkursus.service.models;
import java.time.Instant; import java.util.*;
public record RecipeTemplate(UUID id,String name,String description,boolean active,Instant createdAt,Instant updatedAt,List<RecipeTemplateIngredient> ingredients,List<RecipeTemplateStep> steps) {}
