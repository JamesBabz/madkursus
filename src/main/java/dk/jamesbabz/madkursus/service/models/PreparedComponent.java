package dk.jamesbabz.madkursus.service.models;
import java.util.*;
public record PreparedComponent(UUID id,String key,String name,int sortOrder,List<PreparedComponentIngredient> ingredients,List<RecipePreparationStep> preparationSteps) {}
