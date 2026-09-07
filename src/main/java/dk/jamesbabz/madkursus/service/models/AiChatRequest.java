package dk.jamesbabz.madkursus.service.models;

import java.util.List;

public record AiChatRequest(List<AiChatMessage> messages) {
    public AiChatRequest {
        messages = List.copyOf(messages);
    }
}
