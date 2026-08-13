package dk.jamesbabz.madkursus.service.ports;
import java.util.*;
import dk.jamesbabz.madkursus.service.models.Recipe;
public interface RecipePort {
    Recipe save(Recipe recipe);
    Optional<Recipe> findByIdAndUserId(UUID id, UUID userId);
    List<Recipe> findAllByUserId(UUID userId);
    Optional<Recipe> findByUserIdAndSourceTemplateId(UUID userId,UUID sourceTemplateId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
