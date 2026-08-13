package dk.jamesbabz.madkursus.service.models;

import java.util.UUID;

public record Product(UUID id, UUID userId, UUID sourceTemplateId, String name, ProductCategory category,
                      Unit defaultUnit, InventoryTrackingMode inventoryTrackingMode) {
    public Product(UUID id, UUID userId, String name, ProductCategory category, Unit defaultUnit) {
        this(id, userId, null, name, category, defaultUnit, InventoryTrackingMode.QUANTITY);
    }
}
