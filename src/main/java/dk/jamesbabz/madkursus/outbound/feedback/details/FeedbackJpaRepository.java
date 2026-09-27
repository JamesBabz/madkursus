package dk.jamesbabz.madkursus.outbound.feedback.details;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackJpaRepository extends JpaRepository<FeedbackEntity, UUID> {
    List<FeedbackEntity> findAllByOrderByCreatedAtDescIdDesc();
}
