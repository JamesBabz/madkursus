package dk.jamesbabz.madkursus.inbound.rest.inventory;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import dk.jamesbabz.madkursus.inbound.rest.InventoryApiDelegate;
import dk.jamesbabz.madkursus.inbound.rest.dto.AddInventoryItemDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.AddInventoryQuantityDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.InventoryItemDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.UpdateInventoryItemDTO;
import dk.jamesbabz.madkursus.service.applications.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryApiDelegateImpl implements InventoryApiDelegate {
    private final InventoryService service;
    private final InventoryRestMapper mapper;

    public ResponseEntity<InventoryItemDTO> createInventoryItem(AddInventoryItemDTO request) {
        var item=service.add(request.getProductId(),request.getQuantity());
        InventoryItemDTO result=mapper.toDto(service.getAvailability(item.id()));
        return ResponseEntity.created(URI.create("/v1/inventory/" + result.getId())).body(result);
    }

    public ResponseEntity<InventoryItemDTO> createInventoryItemFromTemplate(
            UUID templateId, AddInventoryQuantityDTO request) {
        var item=service.addFromTemplate(templateId,request.getQuantity());
        InventoryItemDTO result=mapper.toDto(service.getAvailability(item.id()));
        return ResponseEntity.created(URI.create("/v1/inventory/" + result.getId())).body(result);
    }

    public ResponseEntity<List<InventoryItemDTO>> getInventory() {
        return ResponseEntity.ok(service.getAllAvailability().stream().map(mapper::toDto).toList());
    }

    public ResponseEntity<InventoryItemDTO> getInventoryItem(UUID id) {
        return ResponseEntity.ok(mapper.toDto(service.getAvailability(id)));
    }

    public ResponseEntity<Void> updateInventoryItem(UUID id, UpdateInventoryItemDTO request) {
        service.setQuantity(id, request.getQuantity());
        return ResponseEntity.noContent().build();
    }

    public ResponseEntity<Void> deleteInventoryItem(UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
