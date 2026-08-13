package dk.jamesbabz.madkursus.outbound.shoppinglist.details;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import dk.jamesbabz.madkursus.outbound.product.details.ProductEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shopping_list_items")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ShoppingListItemEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID userId;
    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;
    @Column(nullable = false)
    private BigDecimal quantity;
    @Column(nullable = false)
    private boolean purchased;
    private Instant purchasedAt;

    public ShoppingListItemEntity(UUID id, UUID userId, ProductEntity product, BigDecimal quantity,
                                  boolean purchased, Instant purchasedAt) {
        this.id = id; this.userId = userId; this.product = product; this.quantity = quantity;
        this.purchased = purchased; this.purchasedAt = purchasedAt;
    }
}
