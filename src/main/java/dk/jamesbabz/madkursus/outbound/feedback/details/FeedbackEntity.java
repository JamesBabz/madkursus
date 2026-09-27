package dk.jamesbabz.madkursus.outbound.feedback.details;

import dk.jamesbabz.madkursus.service.models.Feedback;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "feedback")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FeedbackEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Feedback.Type type;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, columnDefinition = "text")
    private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Feedback.Status status;
    @Column(nullable = false)
    private UUID createdBy;
    @Column(nullable = false)
    private Instant createdAt;

    public FeedbackEntity(UUID id, Feedback.Type type, String title, String description,
                          Feedback.Status status, UUID createdBy, Instant createdAt) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.description = description;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }
}
