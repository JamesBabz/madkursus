package dk.jamesbabz.madkursus.service.ports;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import dk.jamesbabz.madkursus.service.models.ShoppingListItem;

public interface ShoppingListPort {
    ShoppingListItem save(ShoppingListItem item);
    Optional<ShoppingListItem> findByIdAndUserIdForUpdate(UUID id, UUID userId);
    Optional<ShoppingListItem> findActiveByProductIdAndUserId(UUID productId, UUID userId);
    List<ShoppingListItem> findAllByUserId(UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);
    void deletePurchasedByUserId(UUID userId);
}
