package dk.jamesbabz.madkursus.service.models;

import java.time.Instant;
import java.util.UUID;

public record User(UUID id, String username, String passwordHash, Instant createdAt, boolean enabled) {
}
