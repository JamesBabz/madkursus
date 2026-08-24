package dk.jamesbabz.madkursus.service.applications;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import dk.jamesbabz.madkursus.service.exceptions.InvalidInputException;
import dk.jamesbabz.madkursus.service.exceptions.ConflictException;
import dk.jamesbabz.madkursus.service.models.InventoryItem;
import dk.jamesbabz.madkursus.service.models.Product;
import dk.jamesbabz.madkursus.service.models.ProductTemplate;
import dk.jamesbabz.madkursus.service.models.InventoryTrackingMode;
import dk.jamesbabz.madkursus.service.ports.InventoryPort;
import dk.jamesbabz.madkursus.service.ports.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {
    public record Consumption(BigDecimal deducted, BigDecimal shortage) {}
    private final InventoryPort inventoryPort;
    private final ProductService productService;
    private final ProductTemplateService productTemplateService;
    private final CurrentUserProvider currentUserProvider;
    private final InventoryAvailabilityService availabilityService;

    @Transactional
    public InventoryItem add(UUID productId, BigDecimal quantity) {
        Product product = productService.get(productId);
        UUID userId = currentUserProvider.currentUserId();
        if (product.inventoryTrackingMode() == InventoryTrackingMode.UNTRACKED) {
            throw new InvalidInputException("Untracked products are not stored in inventory");
        }
        if (product.inventoryTrackingMode() == InventoryTrackingMode.PRESENCE) {
            return inventoryPort.findByProductIdAndUserId(productId, userId)
                    .orElseGet(() -> inventoryPort.save(new InventoryItem(null, product, null)));
        }
        requireValidQuantity(quantity, product.defaultUnit());
        return inventoryPort.findByProductIdAndUserId(productId, userId)
                .map(existing -> inventoryPort.save(new InventoryItem(existing.id(), product,
                        existing.quantity().add(quantity))))
                .orElseGet(() -> inventoryPort.save(new InventoryItem(null, product, quantity)));
    }

    @Transactional
    public InventoryItem addFromTemplate(UUID templateId, BigDecimal quantity) {
        ProductTemplate template = productTemplateService.get(templateId);
        if(template.defaultTrackingMode()==InventoryTrackingMode.UNTRACKED)throw new InvalidInputException("Untracked products are not stored in inventory");
        Product product = productService.createFromTemplate(template.id(), template.name(), template.category(),
                template.defaultUnit(), template.defaultTrackingMode());
        return add(product.id(), quantity);
    }

    public InventoryItem get(UUID id) {
        return inventoryPort.findByIdAndUserId(id, currentUserProvider.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item", id));
    }

    public List<InventoryItem> getAll() {
        return inventoryPort.findAllByUserId(currentUserProvider.currentUserId());
    }

    public List<dk.jamesbabz.madkursus.service.models.InventoryAvailability> getAllAvailability() {
        return availabilityService.inventoryAvailability();
    }

    public dk.jamesbabz.madkursus.service.models.InventoryAvailability getAvailability(UUID id) {
        get(id);
        return getAllAvailability().stream().filter(value->value.inventoryItem().id().equals(id)).findFirst()
                .orElseThrow(()->new ResourceNotFoundException("Inventory item",id));
    }

    @Transactional
    public void setQuantity(UUID id, BigDecimal quantity) {
        if (quantity == null || quantity.signum() < 0) {
            throw new InvalidInputException("Quantity must be zero or greater");
        }
        InventoryItem existing = get(id);
        if (existing.product().inventoryTrackingMode() == InventoryTrackingMode.PRESENCE) {
            throw new InvalidInputException("Presence-tracked inventory has no quantity");
        }
        if (quantity.signum() == 0) {
            inventoryPort.deleteByIdAndUserId(id, currentUserProvider.currentUserId());
        } else {
            requireValidQuantity(quantity, existing.unit());
            inventoryPort.save(new InventoryItem(id, existing.product(), quantity));
        }
    }

    @Transactional
    public void delete(UUID id) {
        get(id);
        inventoryPort.deleteByIdAndUserId(id, currentUserProvider.currentUserId());
    }

    @Transactional
    public void removePurchasedQuantity(UUID productId, BigDecimal quantity) {
        Product product = productService.get(productId);
        requireValidQuantity(quantity, product.defaultUnit());
        UUID userId = currentUserProvider.currentUserId();
        InventoryItem existing = inventoryPort.findByProductIdAndUserId(productId, userId)
                .orElseThrow(() -> new ConflictException("Inventory no longer contains the purchased quantity"));
        BigDecimal remaining = existing.quantity().subtract(quantity);
        if (remaining.signum() < 0) {
            throw new ConflictException("Inventory is lower than the quantity added by this purchase");
        }
        if (remaining.signum() == 0) inventoryPort.deleteByIdAndUserId(existing.id(), userId);
        else inventoryPort.save(new InventoryItem(existing.id(), product, remaining));
    }

    public boolean markAvailable(UUID productId) {
        Product product = productService.get(productId);
        UUID userId = currentUserProvider.currentUserId();
        if (inventoryPort.findByProductIdAndUserId(productId, userId).isPresent()) return false;
        inventoryPort.save(new InventoryItem(null, product, null));
        return true;
    }

    public void removeAvailability(UUID productId) {
        UUID userId = currentUserProvider.currentUserId();
        inventoryPort.findByProductIdAndUserId(productId, userId)
                .ifPresent(item -> inventoryPort.deleteByIdAndUserId(item.id(), userId));
    }

    @Transactional
    public Consumption consumeUpToAvailable(UUID productId, BigDecimal requested) {
        Product product = productService.get(productId);
        if (product.inventoryTrackingMode() == InventoryTrackingMode.UNTRACKED) return new Consumption(BigDecimal.ZERO, BigDecimal.ZERO);
        if (product.inventoryTrackingMode() == InventoryTrackingMode.PRESENCE) return new Consumption(BigDecimal.ZERO, BigDecimal.ZERO);
        requireValidQuantity(requested, product.defaultUnit());
        UUID userId = currentUserProvider.currentUserId();
        var item = inventoryPort.findByProductIdAndUserId(productId, userId);
        if (item.isEmpty()) return new Consumption(BigDecimal.ZERO, requested);
        BigDecimal deducted = item.get().quantity().min(requested);
        BigDecimal remaining = item.get().quantity().subtract(deducted);
        if (remaining.signum() == 0) inventoryPort.deleteByIdAndUserId(item.get().id(), userId);
        else inventoryPort.save(new InventoryItem(item.get().id(), product, remaining));
        return new Consumption(deducted, requested.subtract(deducted));
    }

    private void requireValidQuantity(BigDecimal quantity, dk.jamesbabz.madkursus.service.models.Unit unit) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new InvalidInputException("Quantity must be greater than zero");
        }
        BigDecimal increments = unit == dk.jamesbabz.madkursus.service.models.Unit.PIECE
                ? quantity.multiply(BigDecimal.valueOf(2)) : quantity;
        if (increments.stripTrailingZeros().scale() > 0) {
            throw new InvalidInputException(unit == dk.jamesbabz.madkursus.service.models.Unit.PIECE
                    ? "Piece quantity must use increments of 0.5" : "Quantity must be a whole number");
        }
    }
}
