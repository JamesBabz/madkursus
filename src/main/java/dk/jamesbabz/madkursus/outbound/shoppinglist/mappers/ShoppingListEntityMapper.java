package dk.jamesbabz.madkursus.outbound.shoppinglist.mappers;

import dk.jamesbabz.madkursus.outbound.product.mappers.ProductEntityMapper;
import dk.jamesbabz.madkursus.outbound.shoppinglist.details.ShoppingListItemEntity;
import dk.jamesbabz.madkursus.service.models.ShoppingListItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShoppingListEntityMapper {
    private final ProductEntityMapper productMapper;
    public ShoppingListItem toModel(ShoppingListItemEntity entity) {
        return new ShoppingListItem(entity.getId(), entity.getUserId(), productMapper.toModel(entity.getProduct()),
                entity.getQuantity(), entity.isPurchased(), entity.getPurchasedAt(), entity.getInventoryWasPresent());
    }
    public ShoppingListItemEntity toEntity(ShoppingListItem model) {
        return new ShoppingListItemEntity(model.id(), model.userId(), productMapper.toEntity(model.product()),
                model.quantity(), model.purchased(), model.purchasedAt(), model.inventoryWasPresent());
    }
}
