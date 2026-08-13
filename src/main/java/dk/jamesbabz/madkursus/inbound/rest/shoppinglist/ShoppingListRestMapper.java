package dk.jamesbabz.madkursus.inbound.rest.shoppinglist;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import dk.jamesbabz.madkursus.inbound.rest.dto.ShoppingListItemDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.UnitDTO;
import dk.jamesbabz.madkursus.inbound.rest.product.ProductRestMapper;
import dk.jamesbabz.madkursus.service.models.ShoppingListItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShoppingListRestMapper {
    private final ProductRestMapper productMapper;
    public ShoppingListItemDTO toDto(ShoppingListItem item) {
        ShoppingListItemDTO dto = new ShoppingListItemDTO(item.id(), productMapper.toDto(item.product()),
                item.quantity(), UnitDTO.valueOf(item.unit().name()), item.purchased());
        if (item.purchasedAt() != null) dto.setPurchasedAt(OffsetDateTime.ofInstant(item.purchasedAt(), ZoneOffset.UTC));
        return dto;
    }
}
