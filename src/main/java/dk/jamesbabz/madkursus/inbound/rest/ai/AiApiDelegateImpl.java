package dk.jamesbabz.madkursus.inbound.rest.ai;

import dk.jamesbabz.madkursus.inbound.rest.AiApiDelegate;
import dk.jamesbabz.madkursus.inbound.rest.dto.AiChatRequestDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.AiChatResponseDTO;
import dk.jamesbabz.madkursus.service.applications.AiChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiApiDelegateImpl implements AiApiDelegate {
    private final AiChatService service;

    @Override
    public ResponseEntity<AiChatResponseDTO> chat(AiChatRequestDTO request) {
        return ResponseEntity.ok(new AiChatResponseDTO(service.chat(request.getMessage()).answer()));
    }
}
