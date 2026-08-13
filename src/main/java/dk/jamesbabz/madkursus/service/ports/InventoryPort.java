package dk.jamesbabz.madkursus.service.ports;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dk.jamesbabz.madkursus.service.models.InventoryItem;

public interface InventoryPort {
    InventoryItem save(InventoryItem item);
    Optional<InventoryItem> findByIdAndUserId(UUID id, UUID userId);
    Optional<InventoryItem> findByProductIdAndUserId(UUID productId, UUID userId);
    List<InventoryItem> findAllByUserId(UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
