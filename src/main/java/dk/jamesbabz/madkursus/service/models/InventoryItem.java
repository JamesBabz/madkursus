package dk.jamesbabz.madkursus.service.models;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryItem(UUID id, Product product, BigDecimal quantity) {
    public Unit unit() { return product.defaultUnit(); }
}
