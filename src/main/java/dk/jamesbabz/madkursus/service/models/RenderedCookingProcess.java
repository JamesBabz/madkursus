package dk.jamesbabz.madkursus.service.models;

import java.util.List;

public record RenderedCookingProcess(List<String> instructions, String completionCriterion,
        List<String> warnings) {}
