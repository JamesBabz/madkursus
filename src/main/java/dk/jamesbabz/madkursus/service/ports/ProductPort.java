package dk.jamesbabz.madkursus.service.ports;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dk.jamesbabz.madkursus.service.models.Product;

public interface ProductPort {
    Product save(Product product);
    Optional<Product> findByIdAndUserId(UUID id, UUID userId);
    List<Product> findAllByUserId(UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);
    boolean existsByUserIdAndNormalizedName(UUID userId, String normalizedName);
    Optional<Product> findByUserIdAndNormalizedName(UUID userId, String normalizedName);
}
