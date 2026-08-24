package dk.jamesbabz.madkursus.service.models;
import java.util.*;
public record RecipeStep(UUID id, RecipeStepType type, String instruction, int sortOrder, UUID cookingProcessId,
        List<CookingProcessBinding> parameterBindings, RenderedCookingProcess renderedProcess,RecipeStructuredInstruction structuredInstruction) {
    public RecipeStep(UUID id,RecipeStepType type,String instruction,int sortOrder,UUID cookingProcessId,List<CookingProcessBinding> bindings,RenderedCookingProcess rendered){this(id,type,instruction,sortOrder,cookingProcessId,bindings,rendered,null);}
    public RecipeStep(UUID id,String instruction,int sortOrder){this(id,RecipeStepType.TEXT,instruction,sortOrder,null,List.of(),null,null);}
}
