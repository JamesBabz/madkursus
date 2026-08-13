package dk.jamesbabz.madkursus.service.models;

import java.util.UUID;

public record Product(UUID id, UUID userId, String name, ProductCategory category, Unit defaultUnit) {
}
