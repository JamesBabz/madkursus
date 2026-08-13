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
        requirePositiveWhole(quantity);
        Product product = productService.get(productId);
        UUID userId = currentUserProvider.currentUserId();
        return port.findActiveByProductIdAndUserId(productId, userId)
                .map(item -> port.save(new ShoppingListItem(item.id(), userId, product,
                        item.quantity().add(quantity), false, null)))
                .orElseGet(() -> port.save(new ShoppingListItem(null, userId, product, quantity, false, null)));
    }

    @Transactional
    public ShoppingListItem addFromTemplate(UUID templateId, BigDecimal quantity) {
        requirePositiveWhole(quantity);
        ProductTemplate template = templateService.get(templateId);
        Product product = productService.findEquivalent(template.name())
                .orElseGet(() -> productService.create(template.name(), template.category(), template.defaultUnit()));
        return add(product.id(), quantity);
    }

    @Transactional
    public ShoppingListItem update(UUID id, BigDecimal quantity) {
        requirePositiveWhole(quantity);
        ShoppingListItem item = ownedForUpdate(id);
        if (item.purchased()) throw new ConflictException("Undo purchase before editing this item");
        return port.save(new ShoppingListItem(item.id(), item.userId(), item.product(), quantity, false, null));
    }

    @Transactional
    public ShoppingListItem purchase(UUID id) {
        ShoppingListItem item = ownedForUpdate(id);
        if (item.purchased()) return item;
        inventoryService.add(item.product().id(), item.quantity());
        return port.save(new ShoppingListItem(item.id(), item.userId(), item.product(), item.quantity(), true, Instant.now()));
    }

    @Transactional
    public ShoppingListItem undoPurchase(UUID id) {
        ShoppingListItem item = ownedForUpdate(id);
        if (!item.purchased()) throw new ConflictException("Item has not been purchased");
        inventoryService.removePurchasedQuantity(item.product().id(), item.quantity());
        var active = port.findActiveByProductIdAndUserId(item.product().id(), item.userId());
        if (active.isPresent()) {
            ShoppingListItem existing = active.get();
            ShoppingListItem merged = port.save(new ShoppingListItem(existing.id(), existing.userId(),
                    existing.product(), existing.quantity().add(item.quantity()), false, null));
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

    private void requirePositiveWhole(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) throw new InvalidInputException("Quantity must be greater than zero");
        if (quantity.stripTrailingZeros().scale() > 0) throw new InvalidInputException("Quantity must be a whole number");
    }
}
