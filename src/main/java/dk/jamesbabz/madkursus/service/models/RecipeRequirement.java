package dk.jamesbabz.madkursus.service.models;
import java.math.BigDecimal;
public record RecipeRequirement(ProductTemplate productTemplate, Product product, InventoryTrackingMode trackingMode,
 BigDecimal requiredQuantity, Unit unit, BigDecimal availableQuantity, BigDecimal missingQuantity,
 boolean satisfied, String warning) {}
