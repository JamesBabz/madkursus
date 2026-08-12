package dk.jamesbabz.madkursus.inbound.rest.inventory;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import dk.jamesbabz.madkursus.inbound.rest.InventoryApiDelegate;
import dk.jamesbabz.madkursus.inbound.rest.dto.CreateInventoryItemDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.InventoryItemDTO;
import dk.jamesbabz.madkursus.service.applications.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryApiDelegateImpl implements InventoryApiDelegate {
    private final InventoryService service;
    private final InventoryRestMapper mapper;

    public ResponseEntity<InventoryItemDTO> createInventoryItem(CreateInventoryItemDTO request) {
        InventoryItemDTO result = mapper.toDto(service.create(request.getProductId(), request.getQuantity(),
                mapper.toUnit(request.getUnit())));
        return ResponseEntity.created(URI.create("/v1/inventory/" + result.getId())).body(result);
    }

    public ResponseEntity<List<InventoryItemDTO>> getInventory() {
        return ResponseEntity.ok(service.getAll().stream().map(mapper::toDto).toList());
    }

    public ResponseEntity<InventoryItemDTO> getInventoryItem(UUID id) {
        return ResponseEntity.ok(mapper.toDto(service.get(id)));
    }

    public ResponseEntity<InventoryItemDTO> updateInventoryItem(UUID id, CreateInventoryItemDTO request) {
        return ResponseEntity.ok(mapper.toDto(service.update(id, request.getProductId(), request.getQuantity(),
                mapper.toUnit(request.getUnit()))));
    }

    public ResponseEntity<Void> deleteInventoryItem(UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
