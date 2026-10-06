package dk.jamesbabz.madkursus.inbound.rest.feedback;

import dk.jamesbabz.madkursus.inbound.rest.FeedbackApiDelegate;
import dk.jamesbabz.madkursus.inbound.rest.dto.*;
import dk.jamesbabz.madkursus.service.applications.FeedbackService;
import dk.jamesbabz.madkursus.service.models.Feedback;
import dk.jamesbabz.madkursus.service.models.FeedbackDetails;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeedbackApiDelegateImpl implements FeedbackApiDelegate {
    private final FeedbackService service;

    @Override
    public ResponseEntity<Void> submitFeedback(FeedbackInputDTO input) {
        service.submit(Feedback.Type.valueOf(input.getType().name()), input.getTitle(), input.getDescription());
        return ResponseEntity.status(201).build();
    }

    @Override
    public ResponseEntity<List<FeedbackDTO>> getFeedback() {
        return ResponseEntity.ok(service.getAll().stream().map(this::dto).toList());
    }

    @Override
    public ResponseEntity<FeedbackDTO> updateFeedbackStatus(UUID id, FeedbackStatusInputDTO input) {
        return ResponseEntity.ok(dto(service.updateStatus(id, Feedback.Status.valueOf(input.getStatus().name()))));
    }

    @Override
    public ResponseEntity<Void> deleteFeedback(UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private FeedbackDTO dto(FeedbackDetails details) {
        Feedback value = details.feedback();
        return new FeedbackDTO(value.id(), FeedbackTypeDTO.valueOf(value.type().name()), value.title(),
                value.description(), FeedbackStatusDTO.valueOf(value.status().name()), value.createdBy(), details.createdByUsername(),
                value.createdAt().atOffset(ZoneOffset.UTC));
    }
}
