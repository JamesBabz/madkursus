package dk.jamesbabz.madkursus.service.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ShoppingListItem(UUID id, UUID userId, Product product, BigDecimal quantity,
                               boolean purchased, Instant purchasedAt, Boolean inventoryWasPresent) {
    public ShoppingListItem(UUID id, UUID userId, Product product, BigDecimal quantity,
                            boolean purchased, Instant purchasedAt) {
        this(id, userId, product, quantity, purchased, purchasedAt, null);
    }
    public Unit unit() { return product.defaultUnit(); }
}
