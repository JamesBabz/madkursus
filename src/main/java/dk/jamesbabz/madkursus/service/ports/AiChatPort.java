package dk.jamesbabz.madkursus.service.ports;

import dk.jamesbabz.madkursus.service.models.AiChatRequest;
import dk.jamesbabz.madkursus.service.models.AiChatResponse;

public interface AiChatPort {
    AiChatResponse chat(AiChatRequest request);
}
