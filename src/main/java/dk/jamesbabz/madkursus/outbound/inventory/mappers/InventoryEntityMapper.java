package dk.jamesbabz.madkursus.outbound.inventory.mappers;

import dk.jamesbabz.madkursus.outbound.inventory.details.InventoryItemEntity;
import dk.jamesbabz.madkursus.outbound.product.mappers.ProductEntityMapper;
import dk.jamesbabz.madkursus.service.models.InventoryItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryEntityMapper {
    private final ProductEntityMapper productMapper;

    public InventoryItem toModel(InventoryItemEntity entity) {
        return new InventoryItem(entity.getId(), productMapper.toModel(entity.getProduct()), entity.getQuantity());
    }

    public InventoryItemEntity toEntity(InventoryItem model) {
        return new InventoryItemEntity(model.id(), productMapper.toEntity(model.product()), model.quantity());
    }
}
