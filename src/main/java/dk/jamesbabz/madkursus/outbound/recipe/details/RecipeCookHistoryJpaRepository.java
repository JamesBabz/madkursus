package dk.jamesbabz.madkursus.outbound.recipe.details;
import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository;
public interface RecipeCookHistoryJpaRepository extends JpaRepository<RecipeCookHistoryEntity,UUID> {}
