package dk.jamesbabz.madkursus.service.applications;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import dk.jamesbabz.madkursus.service.exceptions.ConflictException;
import dk.jamesbabz.madkursus.service.exceptions.InvalidInputException;
import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import dk.jamesbabz.madkursus.service.models.Product;
import dk.jamesbabz.madkursus.service.models.ProductTemplate;
import dk.jamesbabz.madkursus.service.models.ShoppingListItem;
import dk.jamesbabz.madkursus.service.models.InventoryTrackingMode;
import dk.jamesbabz.madkursus.service.ports.CurrentUserProvider;
import dk.jamesbabz.madkursus.service.ports.ShoppingListPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShoppingListService {
    private final ShoppingListPort port;
    private final ProductService productService;
    private final ProductTemplateService templateService;
    private final InventoryService inventoryService;
    private final CurrentUserProvider currentUserProvider;

    public List<ShoppingListItem> getAll() { return port.findAllByUserId(currentUserProvider.currentUserId()); }

    @Transactional
    public ShoppingListItem add(UUID productId, BigDecimal quantity) {
        Product product = productService.get(productId);
        if(product.inventoryTrackingMode()==InventoryTrackingMode.UNTRACKED)throw new InvalidInputException("Untracked products do not belong on the shopping list");
        requireValidQuantity(product, quantity);
        UUID userId = currentUserProvider.currentUserId();
        return port.findActiveByProductIdAndUserId(productId, userId)
                .map(item -> port.save(new ShoppingListItem(item.id(), userId, product,
                        product.inventoryTrackingMode() == InventoryTrackingMode.PRESENCE ? null
                                : item.quantity().add(quantity), false, null)))
                .orElseGet(() -> port.save(new ShoppingListItem(null, userId, product, quantity, false, null)));
    }

    @Transactional
    public ShoppingListItem addFromTemplate(UUID templateId, BigDecimal quantity) {
        ProductTemplate template = templateService.get(templateId);
        if(template.defaultTrackingMode()==InventoryTrackingMode.UNTRACKED)throw new InvalidInputException("Untracked products do not belong on the shopping list");
        Product product = productService.createFromTemplate(template.id(), template.name(), template.category(),
                template.defaultUnit(), template.defaultTrackingMode());
        return add(product.id(), quantity);
    }

    @Transactional
    public ShoppingListItem ensureAtLeastFromTemplate(UUID templateId, BigDecimal requiredQuantity) {
        ProductTemplate template = templateService.get(templateId);
        if(template.defaultTrackingMode()==InventoryTrackingMode.UNTRACKED)throw new InvalidInputException("Untracked products do not belong on the shopping list");
        Product product = productService.createFromTemplate(template.id(), template.name(), template.category(),
                template.defaultUnit(), template.defaultTrackingMode());
        var active = port.findActiveByProductIdAndUserId(product.id(), currentUserProvider.currentUserId());
        if (product.inventoryTrackingMode() == InventoryTrackingMode.PRESENCE) {
            return active.orElseGet(() -> add(product.id(), null));
        }
        requireValidQuantity(product, requiredQuantity);
        if (active.isPresent() && active.get().quantity().compareTo(requiredQuantity) >= 0) return active.get();
        BigDecimal difference = active.map(item -> requiredQuantity.subtract(item.quantity())).orElse(requiredQuantity);
        return add(product.id(), difference);
    }

    @Transactional
    public ShoppingListItem update(UUID id, BigDecimal quantity) {
        ShoppingListItem item = ownedForUpdate(id);
        if (item.purchased()) throw new ConflictException("Undo purchase before editing this item");
        requireValidQuantity(item.product(), quantity);
        return port.save(new ShoppingListItem(item.id(), item.userId(), item.product(), quantity, false, null));
    }

    @Transactional
    public ShoppingListItem purchase(UUID id) {
        ShoppingListItem item = ownedForUpdate(id);
        if (item.purchased()) return item;
        Boolean wasPresent = null;
        if (item.product().inventoryTrackingMode() == InventoryTrackingMode.PRESENCE) {
            wasPresent = !inventoryService.markAvailable(item.product().id());
        } else {
            inventoryService.add(item.product().id(), item.quantity());
        }
        return port.save(new ShoppingListItem(item.id(), item.userId(), item.product(), item.quantity(), true,
                Instant.now(), wasPresent));
    }

    @Transactional
    public ShoppingListItem undoPurchase(UUID id) {
        ShoppingListItem item = ownedForUpdate(id);
        if (!item.purchased()) throw new ConflictException("Item has not been purchased");
        if (item.product().inventoryTrackingMode() == InventoryTrackingMode.PRESENCE) {
            if (Boolean.FALSE.equals(item.inventoryWasPresent())) inventoryService.removeAvailability(item.product().id());
        } else {
            inventoryService.removePurchasedQuantity(item.product().id(), item.quantity());
        }
        var active = port.findActiveByProductIdAndUserId(item.product().id(), item.userId());
        if (active.isPresent()) {
            ShoppingListItem existing = active.get();
            ShoppingListItem merged = port.save(new ShoppingListItem(existing.id(), existing.userId(),
                    existing.product(), item.product().inventoryTrackingMode() == InventoryTrackingMode.PRESENCE
                            ? null : existing.quantity().add(item.quantity()), false, null));
            port.deleteByIdAndUserId(item.id(), item.userId());
            return merged;
        }
        return port.save(new ShoppingListItem(item.id(), item.userId(), item.product(), item.quantity(), false, null));
    }

    @Transactional
    public void delete(UUID id) {
        ShoppingListItem item = ownedForUpdate(id);
        if (item.purchased()) throw new ConflictException("Undo purchase before deleting this item");
        port.deleteByIdAndUserId(id, currentUserProvider.currentUserId());
    }

    @Transactional
    public void clearPurchased() { port.deletePurchasedByUserId(currentUserProvider.currentUserId()); }

    private ShoppingListItem ownedForUpdate(UUID id) {
        return port.findByIdAndUserIdForUpdate(id, currentUserProvider.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Shopping list item", id));
    }

    private void requireValidQuantity(Product product, BigDecimal quantity) {
        if (product.inventoryTrackingMode() == InventoryTrackingMode.PRESENCE) {
            if (quantity != null) throw new InvalidInputException("Presence-tracked shopping items have no quantity");
            return;
        }
        if (quantity == null || quantity.signum() <= 0) throw new InvalidInputException("Quantity must be greater than zero");
        BigDecimal increments = product.defaultUnit() == dk.jamesbabz.madkursus.service.models.Unit.PIECE
                ? quantity.multiply(BigDecimal.valueOf(2)) : quantity;
        if (increments.stripTrailingZeros().scale() > 0) throw new InvalidInputException(
                product.defaultUnit() == dk.jamesbabz.madkursus.service.models.Unit.PIECE
                        ? "Piece quantity must use increments of 0.5" : "Quantity must be a whole number");
    }
}
