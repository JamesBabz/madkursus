package dk.jamesbabz.madkursus.service.models;

import java.util.UUID;

public record CookingProcessEquipmentRequirement(UUID id, EquipmentType equipmentType,
        EquipmentRequirementLevel level) {}
