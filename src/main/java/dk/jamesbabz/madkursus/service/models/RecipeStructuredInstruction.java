package dk.jamesbabz.madkursus.service.models;
import java.util.List;
public record RecipeStructuredInstruction(List<RecipeInstructionPart> parts) {public RecipeStructuredInstruction{parts=List.copyOf(parts);}}
