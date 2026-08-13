package dk.jamesbabz.madkursus.outbound.shoppinglist;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import dk.jamesbabz.madkursus.outbound.shoppinglist.details.ShoppingListJpaRepository;
import dk.jamesbabz.madkursus.outbound.shoppinglist.mappers.ShoppingListEntityMapper;
import dk.jamesbabz.madkursus.service.models.ShoppingListItem;
import dk.jamesbabz.madkursus.service.ports.ShoppingListPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ShoppingListAdapterImpl implements ShoppingListPort {
    private final ShoppingListJpaRepository repository;
    private final ShoppingListEntityMapper mapper;
    public ShoppingListItem save(ShoppingListItem item) { return mapper.toModel(repository.save(mapper.toEntity(item))); }
    public Optional<ShoppingListItem> findByIdAndUserIdForUpdate(UUID id, UUID userId) { return repository.findOwnedForUpdate(id, userId).map(mapper::toModel); }
    public Optional<ShoppingListItem> findActiveByProductIdAndUserId(UUID productId, UUID userId) { return repository.findByProductIdAndUserIdAndPurchasedFalse(productId, userId).map(mapper::toModel); }
    public List<ShoppingListItem> findAllByUserId(UUID userId) { return repository.findAllByUserIdOrderByPurchasedAscProductNameAsc(userId).stream().map(mapper::toModel).toList(); }
    @Transactional public void deleteByIdAndUserId(UUID id, UUID userId) { repository.deleteByIdAndUserId(id, userId); }
    @Transactional public void deletePurchasedByUserId(UUID userId) { repository.deletePurchased(userId); }
}
