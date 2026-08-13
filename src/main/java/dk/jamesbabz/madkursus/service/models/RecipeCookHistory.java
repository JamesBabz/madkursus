package dk.jamesbabz.madkursus.service.models;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public record RecipeCookHistory(UUID id, UUID userId, UUID recipeId, String recipeName, BigDecimal portions, Instant cookedAt) {}
