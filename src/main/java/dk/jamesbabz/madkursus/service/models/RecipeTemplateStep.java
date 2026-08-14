package dk.jamesbabz.madkursus.service.models;
import java.util.*;
public record RecipeTemplateStep(UUID id,RecipeStepType type,String instruction,int sortOrder,UUID cookingProcessId,
        List<CookingProcessBinding> parameterBindings) {
    public RecipeTemplateStep(UUID id,String instruction,int sortOrder){this(id,RecipeStepType.TEXT,instruction,sortOrder,null,List.of());}
}
