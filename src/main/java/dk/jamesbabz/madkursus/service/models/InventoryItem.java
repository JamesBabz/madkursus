package dk.jamesbabz.madkursus.service.models;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryItem(UUID id, Product product, BigDecimal quantity, Unit unit) {
}
