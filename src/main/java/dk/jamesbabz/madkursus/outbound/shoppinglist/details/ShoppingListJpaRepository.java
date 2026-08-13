package dk.jamesbabz.madkursus.outbound.shoppinglist.details;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShoppingListJpaRepository extends JpaRepository<ShoppingListItemEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ShoppingListItemEntity i where i.id = :id and i.userId = :userId")
    Optional<ShoppingListItemEntity> findOwnedForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    Optional<ShoppingListItemEntity> findByProductIdAndUserIdAndPurchasedFalse(UUID productId, UUID userId);
    List<ShoppingListItemEntity> findAllByUserIdOrderByPurchasedAscProductNameAsc(UUID userId);
    long deleteByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("delete from ShoppingListItemEntity i where i.userId = :userId and i.purchased = true")
    void deletePurchased(@Param("userId") UUID userId);
}
