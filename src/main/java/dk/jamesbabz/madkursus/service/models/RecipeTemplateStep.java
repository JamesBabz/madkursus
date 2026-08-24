package dk.jamesbabz.madkursus.service.models;
import java.util.*;
public record RecipeTemplateStep(UUID id,RecipeStepType type,String instruction,int sortOrder,UUID cookingProcessId,
        List<CookingProcessBinding> parameterBindings, RenderedCookingProcess renderedProcess,RecipeStructuredInstruction structuredInstruction) {
    public RecipeTemplateStep(UUID id,RecipeStepType type,String instruction,int sortOrder,UUID cookingProcessId,List<CookingProcessBinding> parameterBindings,RenderedCookingProcess renderedProcess){this(id,type,instruction,sortOrder,cookingProcessId,parameterBindings,renderedProcess,null);}
    public RecipeTemplateStep(UUID id,RecipeStepType type,String instruction,int sortOrder,UUID cookingProcessId,
            List<CookingProcessBinding> parameterBindings){this(id,type,instruction,sortOrder,cookingProcessId,parameterBindings,null,null);}
    public RecipeTemplateStep(UUID id,String instruction,int sortOrder){this(id,RecipeStepType.TEXT,instruction,sortOrder,null,List.of(),null,null);}
}
