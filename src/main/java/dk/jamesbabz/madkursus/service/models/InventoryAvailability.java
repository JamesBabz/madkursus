package dk.jamesbabz.madkursus.service.models;

import java.math.BigDecimal;
import java.util.List;

public record InventoryAvailability(InventoryItem inventoryItem,BigDecimal physicalQuantity,
        BigDecimal reservedQuantity,BigDecimal availableQuantity,BigDecimal plannedShortfall,
        int plannedUsageCount,List<InventoryReservationDetail> reservations) {}
