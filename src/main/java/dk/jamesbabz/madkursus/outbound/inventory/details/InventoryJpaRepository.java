package dk.jamesbabz.madkursus.outbound.inventory.details;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryJpaRepository extends JpaRepository<InventoryItemEntity, UUID> {
}
