package dk.jamesbabz.madkursus.service.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ShoppingListItem(UUID id, UUID userId, Product product, BigDecimal quantity,
                               boolean purchased, Instant purchasedAt) {
    public Unit unit() { return product.defaultUnit(); }
}
