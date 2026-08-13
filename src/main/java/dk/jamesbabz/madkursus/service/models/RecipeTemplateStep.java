package dk.jamesbabz.madkursus.service.models;
import java.util.UUID;
public record RecipeTemplateStep(UUID id,String instruction,int sortOrder) {}
