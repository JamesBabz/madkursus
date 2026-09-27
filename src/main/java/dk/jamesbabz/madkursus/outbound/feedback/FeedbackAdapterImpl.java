package dk.jamesbabz.madkursus.outbound.feedback;

import dk.jamesbabz.madkursus.outbound.feedback.details.FeedbackJpaRepository;
import dk.jamesbabz.madkursus.outbound.feedback.mappers.FeedbackEntityMapper;
import dk.jamesbabz.madkursus.service.models.Feedback;
import dk.jamesbabz.madkursus.service.ports.FeedbackPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeedbackAdapterImpl implements FeedbackPort {
    private final FeedbackJpaRepository repository;
    private final FeedbackEntityMapper mapper;

    public Feedback save(Feedback value) { return mapper.toModel(repository.save(mapper.toEntity(value))); }
    public List<Feedback> findAll() {
        return repository.findAllByOrderByCreatedAtDescIdDesc().stream().map(mapper::toModel).toList();
    }
    public Optional<Feedback> findById(UUID id) { return repository.findById(id).map(mapper::toModel); }
    public void deleteById(UUID id) { repository.deleteById(id); }
}
