package dk.jamesbabz.madkursus.outbound.feedback.mappers;

import dk.jamesbabz.madkursus.outbound.feedback.details.FeedbackEntity;
import dk.jamesbabz.madkursus.service.models.Feedback;
import org.springframework.stereotype.Component;

@Component
public class FeedbackEntityMapper {
    public Feedback toModel(FeedbackEntity value) {
        return new Feedback(value.getId(), value.getType(), value.getTitle(), value.getDescription(),
                value.getStatus(), value.getCreatedBy(), value.getCreatedAt());
    }
    public FeedbackEntity toEntity(Feedback value) {
        return new FeedbackEntity(value.id(), value.type(), value.title(), value.description(),
                value.status(), value.createdBy(), value.createdAt());
    }
}
