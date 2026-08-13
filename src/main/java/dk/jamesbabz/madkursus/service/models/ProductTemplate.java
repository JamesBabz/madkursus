package dk.jamesbabz.madkursus.service.models;

import java.util.List;
import java.util.UUID;

public record ProductTemplate(UUID id, String name, ProductCategory category, Unit defaultUnit,
                              List<String> aliases, boolean common) {}
