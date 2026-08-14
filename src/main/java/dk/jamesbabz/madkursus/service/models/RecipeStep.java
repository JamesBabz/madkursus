package dk.jamesbabz.madkursus.service.models;
import java.util.*;
public record RecipeStep(UUID id, RecipeStepType type, String instruction, int sortOrder, UUID cookingProcessId,
        List<CookingProcessBinding> parameterBindings, RenderedCookingProcess renderedProcess) {
    public RecipeStep(UUID id,String instruction,int sortOrder){this(id,RecipeStepType.TEXT,instruction,sortOrder,null,List.of(),null);}
}
