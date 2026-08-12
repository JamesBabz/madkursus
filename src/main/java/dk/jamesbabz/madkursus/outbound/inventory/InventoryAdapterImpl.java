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

@Component
@RequiredArgsConstructor
public class InventoryAdapterImpl implements InventoryPort {
    private final InventoryJpaRepository repository;
    private final InventoryEntityMapper mapper;

    public InventoryItem save(InventoryItem item) { return mapper.toModel(repository.save(mapper.toEntity(item))); }
    public Optional<InventoryItem> findById(UUID id) { return repository.findById(id).map(mapper::toModel); }
    public List<InventoryItem> findAll() { return repository.findAll().stream().map(mapper::toModel).toList(); }
    public void deleteById(UUID id) { repository.deleteById(id); }
}
