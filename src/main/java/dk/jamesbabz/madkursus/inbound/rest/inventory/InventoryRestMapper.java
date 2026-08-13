package dk.jamesbabz.madkursus.inbound.rest.inventory;

import dk.jamesbabz.madkursus.inbound.rest.dto.InventoryItemDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.UnitDTO;
import dk.jamesbabz.madkursus.inbound.rest.product.ProductRestMapper;
import dk.jamesbabz.madkursus.service.models.InventoryItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryRestMapper {
    private final ProductRestMapper productMapper;

    public InventoryItemDTO toDto(InventoryItem item) {
        return new InventoryItemDTO(item.id(), productMapper.toDto(item.product()), item.quantity(),
                UnitDTO.valueOf(item.unit().name()));
    }
}
