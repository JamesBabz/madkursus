package dk.jamesbabz.madkursus.outbound.product.details;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID> {
}
