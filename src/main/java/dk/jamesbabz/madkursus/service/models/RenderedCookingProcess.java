package dk.jamesbabz.madkursus.service.models;

import java.util.List;

public record RenderedCookingProcess(List<String> instructions, String completionCriterion,
        List<String> warnings, String processName, Integer activeDurationSeconds,
        Integer passiveDurationSeconds, String durationSummary, List<String> preparationInstructions,String inputSummary) {
    public RenderedCookingProcess(List<String> instructions,String completionCriterion,List<String> warnings) {
        this(instructions,completionCriterion,warnings,null,null,null,null,List.of(),null);
    }
}
