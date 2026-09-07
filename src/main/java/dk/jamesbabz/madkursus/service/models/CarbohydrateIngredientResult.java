package dk.jamesbabz.madkursus.service.models;

import java.math.BigDecimal;
import java.util.UUID;

public record CarbohydrateIngredientResult(UUID recipeIngredientId, UUID productTemplateId, String name,
                                           BigDecimal grams, boolean known) {}
