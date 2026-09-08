package dk.jamesbabz.madkursus.service.ports;

import dk.jamesbabz.madkursus.service.models.AiChatRequest;
import dk.jamesbabz.madkursus.service.models.AiMealProposal;

public interface AiChatPort {
    String configuredModel();
    AiMealProposal chat(AiChatRequest request);
}
