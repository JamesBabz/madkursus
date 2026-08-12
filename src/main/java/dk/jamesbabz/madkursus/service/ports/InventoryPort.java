package dk.jamesbabz.madkursus.service.ports;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dk.jamesbabz.madkursus.service.models.InventoryItem;

public interface InventoryPort {
    InventoryItem save(InventoryItem item);
    Optional<InventoryItem> findById(UUID id);
    List<InventoryItem> findAll();
    void deleteById(UUID id);
}
