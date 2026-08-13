package dk.jamesbabz.madkursus.outbound.product.mappers;

import dk.jamesbabz.madkursus.outbound.product.details.ProductEntity;
import dk.jamesbabz.madkursus.service.models.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductEntityMapper {
    public Product toModel(ProductEntity entity) {
        return new Product(entity.getId(), entity.getUserId(), entity.getSourceTemplateId(), entity.getName(),
                entity.getCategory(), entity.getDefaultUnit(), entity.getInventoryTrackingMode());
    }

    public ProductEntity toEntity(Product model) {
        return new ProductEntity(model.id(), model.userId(), model.sourceTemplateId(), model.name(), model.category(),
                model.defaultUnit(), model.inventoryTrackingMode());
    }
}
