package dk.jamesbabz.madkursus.inbound.rest.shoppinglist;

import dk.jamesbabz.madkursus.inbound.rest.ShoppingListTextApiDelegate;
import dk.jamesbabz.madkursus.inbound.rest.dto.*;
import dk.jamesbabz.madkursus.service.applications.ShoppingListTextService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShoppingListTextApiDelegateImpl implements ShoppingListTextApiDelegate {
    private final ShoppingListTextService service;
    @Override public ResponseEntity<ShoppingListTextResultDTO> previewShoppingListText(String body) { return response(service.preview(body)); }
    @Override public ResponseEntity<ShoppingListTextResultDTO> importShoppingListText(String body) { return response(service.importText(body)); }
    private ResponseEntity<ShoppingListTextResultDTO> response(ShoppingListTextService.Result result) {
        return ResponseEntity.ok(new ShoppingListTextResultDTO().valid(result.valid()).imported(result.imported())
                .items(result.items().stream().map(line -> new ShoppingListTextLineDTO().line(line.line()).text(line.text())
                        .name(line.name()).quantity(line.quantity()).unit(line.unit() == null ? null : UnitDTO.valueOf(line.unit().name()))
                        .newProduct(line.newProduct()).error(line.error())).toList()));
    }
}
