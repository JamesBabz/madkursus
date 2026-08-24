package dk.jamesbabz.madkursus.service.models;
import java.util.UUID;
public record RecipePreparationStep(UUID id,String instruction,int sortOrder,RecipeStructuredInstruction structuredInstruction) {public RecipePreparationStep(UUID id,String instruction,int sortOrder){this(id,instruction,sortOrder,null);}}
