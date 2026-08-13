package dk.jamesbabz.madkursus.outbound.product.details;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID> {
    Optional<ProductEntity> findByIdAndUserId(UUID id, UUID userId);
    List<ProductEntity> findAllByUserIdOrderByNameAsc(UUID userId);
    long deleteByIdAndUserId(UUID id, UUID userId);
}
