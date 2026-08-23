package dk.jamesbabz.madkursus.service.models;
import java.util.UUID;
public record RecipeEquipmentRequirement(UUID id,EquipmentType equipmentType,String label,int sortOrder) {}
