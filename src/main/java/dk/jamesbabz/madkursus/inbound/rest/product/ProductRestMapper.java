package dk.jamesbabz.madkursus.inbound.rest.product;

import dk.jamesbabz.madkursus.inbound.rest.dto.ProductCategoryDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.ProductDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.UnitDTO;
import dk.jamesbabz.madkursus.service.models.Product;
import dk.jamesbabz.madkursus.service.models.ProductCategory;
import dk.jamesbabz.madkursus.service.models.Unit;
import org.springframework.stereotype.Component;

@Component
public class ProductRestMapper {
    public ProductDTO toDto(Product product) {
        return new ProductDTO(product.name(), ProductCategoryDTO.valueOf(product.category().name()),
                UnitDTO.valueOf(product.defaultUnit().name()), product.id());
    }

    public ProductCategory toCategory(ProductCategoryDTO category) { return ProductCategory.valueOf(category.name()); }
    public Unit toUnit(UnitDTO unit) { return Unit.valueOf(unit.name()); }
}
