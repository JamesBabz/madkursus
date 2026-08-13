package dk.jamesbabz.madkursus.outbound.inventory.details;

import java.math.BigDecimal;
import java.util.UUID;

import dk.jamesbabz.madkursus.outbound.product.details.ProductEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inventory_items")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class InventoryItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;
    private BigDecimal quantity;
    public InventoryItemEntity(UUID id, ProductEntity product, BigDecimal quantity) {
        this.id = id;
        this.product = product;
        this.quantity = quantity;
    }
}
