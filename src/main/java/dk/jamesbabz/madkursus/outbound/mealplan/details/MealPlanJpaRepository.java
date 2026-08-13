package dk.jamesbabz.madkursus.outbound.mealplan.details;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface MealPlanJpaRepository extends JpaRepository<MealPlanEntity,UUID>{Optional<MealPlanEntity> findByIdAndUserId(UUID id,UUID userId);List<MealPlanEntity> findAllByUserIdOrderByUpdatedAtDesc(UUID userId);long deleteByIdAndUserId(UUID id,UUID userId);}
