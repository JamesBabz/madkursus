package dk.jamesbabz.madkursus.service.applications;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import dk.jamesbabz.madkursus.service.models.Product;
import dk.jamesbabz.madkursus.service.models.ProductCategory;
import dk.jamesbabz.madkursus.service.models.Unit;
import dk.jamesbabz.madkursus.service.models.InventoryTrackingMode;
import dk.jamesbabz.madkursus.service.models.InventoryItem;
import dk.jamesbabz.madkursus.service.ports.InventoryPort;
import org.springframework.transaction.annotation.Transactional;
import dk.jamesbabz.madkursus.service.ports.ProductPort;
import dk.jamesbabz.madkursus.service.ports.CurrentUserProvider;
import dk.jamesbabz.madkursus.service.exceptions.DuplicateProductException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductPort productPort;
    private final CurrentUserProvider currentUserProvider;
    private final InventoryPort inventoryPort;

    public Product create(String name, ProductCategory category, Unit defaultUnit) {
        UUID userId = currentUserProvider.currentUserId();
        String normalizedName = name.trim();
        if (productPort.existsByUserIdAndNormalizedName(userId, normalizedName)) {
            throw new DuplicateProductException(normalizedName);
        }
        return productPort.save(new Product(null, userId, null, normalizedName, category, defaultUnit,
                InventoryTrackingMode.QUANTITY));
    }

    public Product get(UUID id) {
        return productPort.findByIdAndUserId(id, currentUserProvider.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    public List<Product> getAll() { return productPort.findAllByUserId(currentUserProvider.currentUserId()); }

    public Optional<Product> findEquivalent(String name) {
        return productPort.findByUserIdAndNormalizedName(currentUserProvider.currentUserId(), name.trim());
    }

    public Optional<Product> findEquivalent(UUID templateId, String name) {
        UUID userId = currentUserProvider.currentUserId();
        return productPort.findByUserIdAndSourceTemplateId(userId, templateId)
                .or(() -> productPort.findByUserIdAndNormalizedName(userId, name.trim()));
    }

    public Product createFromTemplate(UUID templateId, String name, ProductCategory category, Unit defaultUnit,
                                      InventoryTrackingMode defaultTrackingMode) {
        UUID userId = currentUserProvider.currentUserId();
        Optional<Product> equivalent = findEquivalent(templateId, name);
        if (equivalent.isPresent()) return equivalent.get();
        return productPort.save(new Product(null, userId, templateId, name.trim(), category, defaultUnit,
                defaultTrackingMode));
    }

    @Transactional
    public Product update(UUID id, String name, ProductCategory category, Unit defaultUnit,
                          InventoryTrackingMode trackingMode) {
        Product existing = get(id);
        InventoryTrackingMode resolvedTrackingMode = trackingMode == null ? existing.inventoryTrackingMode() : trackingMode;
        UUID userId = currentUserProvider.currentUserId();
        if (existing.inventoryTrackingMode() != resolvedTrackingMode) {
            inventoryPort.findByProductIdAndUserId(id, userId).ifPresent(item -> {
                if (resolvedTrackingMode == InventoryTrackingMode.PRESENCE) {
                    inventoryPort.save(new InventoryItem(item.id(), existing, null));
                } else {
                    inventoryPort.deleteByIdAndUserId(item.id(), userId);
                }
            });
        }
        return productPort.save(new Product(id, userId, existing.sourceTemplateId(), name, category, defaultUnit,
                resolvedTrackingMode));
    }

    public Product update(UUID id, String name, ProductCategory category, Unit defaultUnit) {
        return update(id, name, category, defaultUnit, get(id).inventoryTrackingMode());
    }

    public void delete(UUID id) {
        get(id);
        productPort.deleteByIdAndUserId(id, currentUserProvider.currentUserId());
    }
}
