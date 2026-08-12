package dk.jamesbabz.madkursus.service.applications;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import dk.jamesbabz.madkursus.service.models.InventoryItem;
import dk.jamesbabz.madkursus.service.models.Product;
import dk.jamesbabz.madkursus.service.models.Unit;
import dk.jamesbabz.madkursus.service.ports.InventoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryPort inventoryPort;
    private final ProductService productService;

    public InventoryItem create(UUID productId, BigDecimal quantity, Unit unit) {
        Product product = productService.get(productId);
        return inventoryPort.save(new InventoryItem(null, product, quantity, unit));
    }

    public InventoryItem get(UUID id) {
        return inventoryPort.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item", id));
    }

    public List<InventoryItem> getAll() { return inventoryPort.findAll(); }

    public InventoryItem update(UUID id, UUID productId, BigDecimal quantity, Unit unit) {
        get(id);
        Product product = productService.get(productId);
        return inventoryPort.save(new InventoryItem(id, product, quantity, unit));
    }

    public void delete(UUID id) {
        get(id);
        inventoryPort.deleteById(id);
    }
}
