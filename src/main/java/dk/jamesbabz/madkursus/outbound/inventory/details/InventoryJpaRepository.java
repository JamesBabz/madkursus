package dk.jamesbabz.madkursus.outbound.inventory.details;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryJpaRepository extends JpaRepository<InventoryItemEntity, UUID> {
    Optional<InventoryItemEntity> findByIdAndProductUserId(UUID id, UUID userId);
    Optional<InventoryItemEntity> findByProductIdAndProductUserId(UUID productId, UUID userId);
    List<InventoryItemEntity> findAllByProductUserIdOrderByProductNameAsc(UUID userId);
    long deleteByIdAndProductUserId(UUID id, UUID userId);
}
