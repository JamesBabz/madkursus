package dk.jamesbabz.madkursus.service.models;

import java.util.UUID;

public record CookingProcessParameter(UUID id, String key, String label, CookingProcessParameterType type,
        boolean required, CookingProcessValue defaultValue, RecipeUnit unit, int sortOrder) {}
