package dk.jamesbabz.madkursus.outbound.recipe;
import dk.jamesbabz.madkursus.outbound.recipe.details.*; import dk.jamesbabz.madkursus.service.models.RecipeCookHistory; import dk.jamesbabz.madkursus.service.ports.RecipeCookHistoryPort; import jakarta.persistence.EntityManager; import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class RecipeCookHistoryAdapterImpl implements RecipeCookHistoryPort {
    private final RecipeCookHistoryJpaRepository repository;
    private final EntityManager entityManager;

    public void lockUserCompletion(java.util.UUID userId) {
        entityManager.lock(entityManager.getReference(dk.jamesbabz.madkursus.outbound.user.details.UserEntity.class, userId), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }

    public java.util.Optional<RecipeCookHistory> findByIdAndUserId(java.util.UUID id, java.util.UUID userId) {
        return repository.findByIdAndUserId(id, userId).map(this::map);
    }

    public RecipeCookHistory save(RecipeCookHistory h) {
        var entity = new RecipeCookHistoryEntity(h.id() == null ? java.util.UUID.randomUUID() : h.id(), h.userId(),
                entityManager.getReference(RecipeEntity.class, h.recipeId()), h.recipeName(), h.portions(), h.cookedAt());
        // History is append-only: an existing completion ID must never be merged into another user's row.
        entityManager.persist(entity);
        entityManager.flush();
        return map(entity);
    }

    private RecipeCookHistory map(RecipeCookHistoryEntity e) {
        return new RecipeCookHistory(e.getId(), e.getUserId(), e.getRecipe() == null ? null : e.getRecipe().getId(),
                e.getRecipeName(), e.getPortions(), e.getCookedAt());
    }
}
