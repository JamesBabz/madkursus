package dk.jamesbabz.madkursus.service.models;

import java.util.UUID;

public record CookingProcessBinding(UUID id, String parameterKey, UUID recipeIngredientId,
        ProductTemplate productTemplate, CookingProcessValue value) {
    public CookingProcessBinding(UUID id, String parameterKey, ProductTemplate productTemplate,
            CookingProcessValue value) {
        this(id, parameterKey, null, productTemplate, value);
    }
}
