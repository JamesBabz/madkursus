package dk.jamesbabz.madkursus.service.models;

import java.time.Instant;
import java.util.UUID;

public record Feedback(UUID id, Type type, String title, String description, Status status,
                       UUID createdBy, Instant createdAt) {
    public enum Type { FEEDBACK, BUG }
    public enum Status { OPEN, IN_PROGRESS, DONE }
}
