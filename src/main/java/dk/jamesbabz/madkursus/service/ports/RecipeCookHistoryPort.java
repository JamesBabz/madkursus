package dk.jamesbabz.madkursus.service.ports;
import dk.jamesbabz.madkursus.service.models.RecipeCookHistory;
public interface RecipeCookHistoryPort {
    RecipeCookHistory save(RecipeCookHistory history);
    java.util.Optional<RecipeCookHistory> findByIdAndUserId(java.util.UUID id, java.util.UUID userId);
    void lockUserCompletion(java.util.UUID userId);
}
