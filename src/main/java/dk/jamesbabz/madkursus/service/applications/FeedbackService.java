package dk.jamesbabz.madkursus.service.applications;

import dk.jamesbabz.madkursus.service.exceptions.InvalidInputException;
import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import dk.jamesbabz.madkursus.service.models.Feedback;
import dk.jamesbabz.madkursus.service.models.FeedbackDetails;
import dk.jamesbabz.madkursus.service.ports.CurrentUserProvider;
import dk.jamesbabz.madkursus.service.ports.FeedbackPort;
import dk.jamesbabz.madkursus.service.ports.UserPort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackService {
    private final FeedbackPort port;
    private final CurrentUserProvider currentUser;
    private final UserPort users;

    @Transactional
    public void submit(Feedback.Type type, String title, String description) {
        if (type == null) throw new InvalidInputException("Feedback type is required");
        if (title == null || title.isBlank() || title.length() > 200)
            throw new InvalidInputException("Feedback title must contain between 1 and 200 characters");
        if (description == null || description.isBlank() || description.length() > 10000)
            throw new InvalidInputException("Feedback description must contain between 1 and 10000 characters");
        port.save(new Feedback(null, type, title.trim(), description.trim(), Feedback.Status.OPEN,
                currentUser.currentUserId(), Instant.now()));
    }

    public List<FeedbackDetails> getAll() { return details(port.findAll()); }

    private List<FeedbackDetails> details(List<Feedback> values) {
        var usernames = users.findUsernamesByIds(values.stream().map(Feedback::createdBy).distinct().toList());
        return values.stream().map(value -> new FeedbackDetails(value, usernames.get(value.createdBy()))).toList();
    }

    @Transactional
    public FeedbackDetails updateStatus(UUID id, Feedback.Status status) {
        if (status == null) throw new InvalidInputException("Feedback status is required");
        Feedback old = get(id);
        return details(List.of(port.save(new Feedback(old.id(), old.type(), old.title(), old.description(), status,
                old.createdBy(), old.createdAt())))).getFirst();
    }

    @Transactional
    public void delete(UUID id) { get(id); port.deleteById(id); }

    private Feedback get(UUID id) {
        return port.findById(id).orElseThrow(() -> new ResourceNotFoundException("Feedback", id));
    }
}
