package dk.jamesbabz.madkursus.service.models;

import java.util.UUID;

/** A preparation instruction contributed by a process for one input or ingredient-set input. */
public record CookingProcessPreparationRequirement(UUID id, String parameterKey,
        String instructionTemplate, int sortOrder) {}
