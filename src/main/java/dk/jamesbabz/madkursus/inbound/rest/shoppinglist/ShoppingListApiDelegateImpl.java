package dk.jamesbabz.madkursus.inbound.rest.shoppinglist;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import dk.jamesbabz.madkursus.inbound.rest.ShoppingListApiDelegate;
import dk.jamesbabz.madkursus.inbound.rest.dto.AddShoppingListItemDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.ShoppingListItemDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.ShoppingListQuantityDTO;
import dk.jamesbabz.madkursus.service.applications.ShoppingListService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShoppingListApiDelegateImpl implements ShoppingListApiDelegate {
    private final ShoppingListService service;
    private final ShoppingListRestMapper mapper;
    public ResponseEntity<List<ShoppingListItemDTO>> getShoppingList() { return ResponseEntity.ok(service.getAll().stream().map(mapper::toDto).toList()); }
    public ResponseEntity<ShoppingListItemDTO> addShoppingListItem(AddShoppingListItemDTO request) {
        ShoppingListItemDTO item = mapper.toDto(service.add(request.getProductId(), request.getQuantity()));
        return ResponseEntity.created(URI.create("/v1/shopping-list/items/" + item.getId())).body(item);
    }
    public ResponseEntity<ShoppingListItemDTO> addShoppingListItemFromTemplate(UUID templateId, ShoppingListQuantityDTO request) {
        ShoppingListItemDTO item = mapper.toDto(service.addFromTemplate(templateId, request.getQuantity()));
        return ResponseEntity.created(URI.create("/v1/shopping-list/items/" + item.getId())).body(item);
    }
    public ResponseEntity<ShoppingListItemDTO> updateShoppingListItem(UUID id, ShoppingListQuantityDTO request) { return ResponseEntity.ok(mapper.toDto(service.update(id, request.getQuantity()))); }
    public ResponseEntity<ShoppingListItemDTO> purchaseShoppingListItem(UUID id) { return ResponseEntity.ok(mapper.toDto(service.purchase(id))); }
    public ResponseEntity<ShoppingListItemDTO> undoShoppingListItemPurchase(UUID id) { return ResponseEntity.ok(mapper.toDto(service.undoPurchase(id))); }
    public ResponseEntity<Void> deleteShoppingListItem(UUID id) { service.delete(id); return ResponseEntity.noContent().build(); }
    public ResponseEntity<Void> clearPurchasedShoppingListItems() { service.clearPurchased(); return ResponseEntity.noContent().build(); }
}
