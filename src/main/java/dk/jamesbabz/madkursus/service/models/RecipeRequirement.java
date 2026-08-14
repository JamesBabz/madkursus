package dk.jamesbabz.madkursus.service.models;
import java.math.BigDecimal;
public record RecipeRequirement(ProductTemplate productTemplate, Product product, InventoryTrackingMode trackingMode,
 BigDecimal requiredQuantity, Unit unit, BigDecimal physicalQuantity, BigDecimal reservedQuantity,
 BigDecimal availableQuantity, BigDecimal plannedShortfall, BigDecimal missingQuantity, int plannedUsageCount,
 java.util.List<InventoryReservationDetail> reservations, boolean satisfied, String warning) {
 public RecipeRequirement(ProductTemplate template,Product product,InventoryTrackingMode mode,BigDecimal required,Unit unit,
         BigDecimal available,BigDecimal missing,boolean satisfied,String warning){this(template,product,mode,required,unit,
         available,BigDecimal.ZERO,available,BigDecimal.ZERO,missing,0,java.util.List.of(),satisfied,warning);}
}
