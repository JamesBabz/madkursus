package dk.jamesbabz.madkursus.service.models;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryReservationDetail(UUID mealPlanId,String mealPlanName,UUID plannedRecipeId,
        UUID recipeId,String recipeName,int portions,BigDecimal reservedQuantity,Unit unit) {}
