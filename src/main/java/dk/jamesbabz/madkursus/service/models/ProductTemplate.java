package dk.jamesbabz.madkursus.service.models;

import java.util.List;
import java.util.UUID;

public record ProductTemplate(UUID id, String name, ProductCategory category, Unit defaultUnit,
                              InventoryTrackingMode defaultTrackingMode, List<String> aliases, boolean common,
                              List<ProductTemplateUnitConversion> conversions) {
    public ProductTemplate { aliases=List.copyOf(aliases);conversions=List.copyOf(conversions); }
    public ProductTemplate(UUID id,String name,ProductCategory category,Unit defaultUnit,
                           InventoryTrackingMode trackingMode,List<String> aliases,boolean common){
        this(id,name,category,defaultUnit,trackingMode,aliases,common,List.of());
    }
    public ProductTemplate(UUID id, String name, ProductCategory category, Unit defaultUnit,
                           List<String> aliases, boolean common) {
        this(id, name, category, defaultUnit, InventoryTrackingMode.QUANTITY, aliases, common,List.of());
    }
}
