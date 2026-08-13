package dk.jamesbabz.madkursus.service.models;
import java.util.UUID;
public record RecipeStep(UUID id, String instruction, int sortOrder) {}
