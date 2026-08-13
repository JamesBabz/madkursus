package dk.jamesbabz.madkursus.service.applications;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import dk.jamesbabz.madkursus.service.exceptions.InvalidInputException;
import dk.jamesbabz.madkursus.service.models.InventoryItem;
import dk.jamesbabz.madkursus.service.models.Product;
import dk.jamesbabz.madkursus.service.models.ProductTemplate;
import dk.jamesbabz.madkursus.service.ports.InventoryPort;
import dk.jamesbabz.madkursus.service.ports.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryPort inventoryPort;
    private final ProductService productService;
    private final ProductTemplateService productTemplateService;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public InventoryItem add(UUID productId, BigDecimal quantity) {
        requirePositive(quantity);
        Product product = productService.get(productId);
        UUID userId = currentUserProvider.currentUserId();
        return inventoryPort.findByProductIdAndUserId(productId, userId)
                .map(existing -> inventoryPort.save(new InventoryItem(existing.id(), product,
                        existing.quantity().add(quantity))))
                .orElseGet(() -> inventoryPort.save(new InventoryItem(null, product, quantity)));
    }

    @Transactional
    public InventoryItem addFromTemplate(UUID templateId, BigDecimal quantity) {
        requirePositive(quantity);
        ProductTemplate template = productTemplateService.get(templateId);
        Product product = productService.findEquivalent(template.name())
                .orElseGet(() -> productService.create(template.name(), template.category(), template.defaultUnit()));
        return add(product.id(), quantity);
    }

    public InventoryItem get(UUID id) {
        return inventoryPort.findByIdAndUserId(id, currentUserProvider.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item", id));
    }

    public List<InventoryItem> getAll() {
        return inventoryPort.findAllByUserId(currentUserProvider.currentUserId());
    }

    @Transactional
    public void setQuantity(UUID id, BigDecimal quantity) {
        if (quantity == null || quantity.signum() < 0) {
            throw new InvalidInputException("Quantity must be zero or greater");
        }
        requireWholeNumber(quantity);
        InventoryItem existing = get(id);
        if (quantity.signum() == 0) {
            inventoryPort.deleteByIdAndUserId(id, currentUserProvider.currentUserId());
        } else {
            inventoryPort.save(new InventoryItem(id, existing.product(), quantity));
        }
    }

    @Transactional
    public void delete(UUID id) {
        get(id);
        inventoryPort.deleteByIdAndUserId(id, currentUserProvider.currentUserId());
    }

    private void requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new InvalidInputException("Quantity must be greater than zero");
        }
        requireWholeNumber(quantity);
    }

    private void requireWholeNumber(BigDecimal quantity) {
        if (quantity.stripTrailingZeros().scale() > 0) {
            throw new InvalidInputException("Quantity must be a whole number");
        }
    }
}
