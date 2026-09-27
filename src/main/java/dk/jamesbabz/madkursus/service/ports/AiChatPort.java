package dk.jamesbabz.madkursus.service.ports;

import dk.jamesbabz.madkursus.service.models.AiChatRequest;
import dk.jamesbabz.madkursus.service.models.AiMealProposal;

public interface AiChatPort {
    String configuredModel();
    AiMealProposal chat(AiChatRequest request);
    /** Read-only cooking explanation; no recipe proposal or application-state claims. */
    String answerCooking(AiChatRequest request);
}
