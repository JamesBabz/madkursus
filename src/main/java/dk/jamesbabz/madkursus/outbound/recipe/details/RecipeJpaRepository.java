package dk.jamesbabz.madkursus.outbound.recipe.details;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface RecipeJpaRepository extends JpaRepository<RecipeEntity,UUID>{ Optional<RecipeEntity> findByIdAndUserId(UUID id,UUID userId); Optional<RecipeEntity> findByUserIdAndSourceTemplateId(UUID userId,UUID sourceTemplateId); List<RecipeEntity> findAllByUserIdOrderByUpdatedAtDesc(UUID userId); long deleteByIdAndUserId(UUID id,UUID userId); }
