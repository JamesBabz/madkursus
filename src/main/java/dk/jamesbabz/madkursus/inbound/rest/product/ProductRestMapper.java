package dk.jamesbabz.madkursus.inbound.rest.product;

import dk.jamesbabz.madkursus.inbound.rest.dto.ProductCategoryDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.ProductDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.UnitDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.InventoryTrackingModeDTO;
import dk.jamesbabz.madkursus.service.models.Product;
import dk.jamesbabz.madkursus.service.models.ProductCategory;
import dk.jamesbabz.madkursus.service.models.Unit;
import org.springframework.stereotype.Component;

@Component
public class ProductRestMapper {
    public ProductDTO toDto(Product product) {
        ProductDTO dto = new ProductDTO(product.name(), ProductCategoryDTO.valueOf(product.category().name()),
                UnitDTO.valueOf(product.defaultUnit().name()),
                InventoryTrackingModeDTO.valueOf(product.inventoryTrackingMode().name()), product.id());
        dto.setSourceTemplateId(product.sourceTemplateId());
        return dto;
    }

    public ProductCategory toCategory(ProductCategoryDTO category) { return ProductCategory.valueOf(category.name()); }
    public Unit toUnit(UnitDTO unit) { return Unit.valueOf(unit.name()); }
    public dk.jamesbabz.madkursus.service.models.InventoryTrackingMode toTrackingMode(InventoryTrackingModeDTO mode) {
        return mode == null ? null : dk.jamesbabz.madkursus.service.models.InventoryTrackingMode.valueOf(mode.name());
    }
}
