package dk.jamesbabz.madkursus.inbound.rest.inventory;

import dk.jamesbabz.madkursus.inbound.rest.dto.InventoryItemDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.UnitDTO;
import dk.jamesbabz.madkursus.inbound.rest.product.ProductRestMapper;
import dk.jamesbabz.madkursus.service.models.InventoryItem;
import dk.jamesbabz.madkursus.service.models.InventoryAvailability;
import dk.jamesbabz.madkursus.service.models.InventoryReservationDetail;
import dk.jamesbabz.madkursus.inbound.rest.dto.InventoryReservationDetailDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryRestMapper {
    private final ProductRestMapper productMapper;

    public InventoryItemDTO toDto(InventoryItem item) {
        return new InventoryItemDTO(item.id(),productMapper.toDto(item.product()),0,java.util.List.of(),
                UnitDTO.valueOf(item.unit().name()),true).quantity(item.quantity()).physicalQuantity(item.quantity())
                .reservedQuantity(item.quantity()==null?null:java.math.BigDecimal.ZERO).availableQuantity(item.quantity())
                .plannedShortfall(item.quantity()==null?null:java.math.BigDecimal.ZERO);
    }

    public InventoryItemDTO toDto(InventoryAvailability value) {
        InventoryItem item=value.inventoryItem();
        return new InventoryItemDTO(item.id(),productMapper.toDto(item.product()),value.plannedUsageCount(),
                value.reservations().stream().map(this::detail).toList(),UnitDTO.valueOf(item.unit().name()),true)
                .quantity(item.quantity()).physicalQuantity(value.physicalQuantity()).reservedQuantity(value.reservedQuantity())
                .availableQuantity(value.availableQuantity()).plannedShortfall(value.plannedShortfall());
    }

    public InventoryReservationDetailDTO detail(InventoryReservationDetail value) {
        return new InventoryReservationDetailDTO(value.mealPlanId(),value.mealPlanName(),value.plannedRecipeId(),
                value.recipeId(),value.recipeName(),value.portions(),UnitDTO.valueOf(value.unit().name()))
                .reservedQuantity(value.reservedQuantity());
    }
}
