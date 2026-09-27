package dk.jamesbabz.madkursus.service.ports;

import dk.jamesbabz.madkursus.service.models.Feedback;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeedbackPort {
    Feedback save(Feedback feedback);
    List<Feedback> findAll();
    Optional<Feedback> findById(UUID id);
    void deleteById(UUID id);
}
