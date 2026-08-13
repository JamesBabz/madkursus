package dk.jamesbabz.madkursus.service.applications;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import dk.jamesbabz.madkursus.service.models.Product;
import dk.jamesbabz.madkursus.service.models.ProductCategory;
import dk.jamesbabz.madkursus.service.models.Unit;
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

    public Product create(String name, ProductCategory category, Unit defaultUnit) {
        UUID userId = currentUserProvider.currentUserId();
        String normalizedName = name.trim();
        if (productPort.existsByUserIdAndNormalizedName(userId, normalizedName)) {
            throw new DuplicateProductException(normalizedName);
        }
        return productPort.save(new Product(null, userId, normalizedName, category, defaultUnit));
    }

    public Product get(UUID id) {
        return productPort.findByIdAndUserId(id, currentUserProvider.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    public List<Product> getAll() { return productPort.findAllByUserId(currentUserProvider.currentUserId()); }

    public Optional<Product> findEquivalent(String name) {
        return productPort.findByUserIdAndNormalizedName(currentUserProvider.currentUserId(), name.trim());
    }

    public Product update(UUID id, String name, ProductCategory category, Unit defaultUnit) {
        get(id);
        return productPort.save(new Product(id, currentUserProvider.currentUserId(), name, category, defaultUnit));
    }

    public void delete(UUID id) {
        get(id);
        productPort.deleteByIdAndUserId(id, currentUserProvider.currentUserId());
    }
}
