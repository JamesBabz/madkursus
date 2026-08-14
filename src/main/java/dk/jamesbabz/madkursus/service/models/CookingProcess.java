package dk.jamesbabz.madkursus.service.models;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CookingProcess(UUID id, String key, String name, String description, boolean active,
        Instant createdAt, Instant updatedAt, List<CookingProcessParameter> parameters,
        List<CookingProcessStep> steps, List<CookingProcessEquipmentRequirement> equipmentRequirements,
        String completionCriteriaTemplate) {}
