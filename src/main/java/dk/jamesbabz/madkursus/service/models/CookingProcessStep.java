package dk.jamesbabz.madkursus.service.models;

import java.util.UUID;

public record CookingProcessStep(UUID id, String instructionTemplate, int sortOrder) {}
