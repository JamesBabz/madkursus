package dk.jamesbabz.madkursus.outbound.inventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dk.jamesbabz.madkursus.outbound.inventory.details.InventoryJpaRepository;
import dk.jamesbabz.madkursus.outbound.inventory.mappers.InventoryEntityMapper;
import dk.jamesbabz.madkursus.service.models.InventoryItem;
import dk.jamesbabz.madkursus.service.ports.InventoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class InventoryAdapterImpl implements InventoryPort {
    private final InventoryJpaRepository repository;
    private final InventoryEntityMapper mapper;

    public InventoryItem save(InventoryItem item) { return mapper.toModel(repository.save(mapper.toEntity(item))); }
    public Optional<InventoryItem> findByIdAndUserId(UUID id, UUID userId) {
        return repository.findByIdAndProductUserId(id, userId).map(mapper::toModel);
    }
    public Optional<InventoryItem> findByProductIdAndUserId(UUID productId, UUID userId) {
        return repository.findByProductIdAndProductUserId(productId, userId).map(mapper::toModel);
    }
    public List<InventoryItem> findAllByUserId(UUID userId) {
        return repository.findAllByProductUserIdOrderByProductNameAsc(userId).stream().map(mapper::toModel).toList();
    }
    @Transactional
    public void deleteByIdAndUserId(UUID id, UUID userId) { repository.deleteByIdAndProductUserId(id, userId); }
}
