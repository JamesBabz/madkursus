package dk.jamesbabz.madkursus.inbound.rest.cookingprocess;

import java.util.List;
import java.util.UUID;

import dk.jamesbabz.madkursus.inbound.rest.CookingProcessApiDelegate;
import dk.jamesbabz.madkursus.inbound.rest.dto.CookingProcessDTO;
import dk.jamesbabz.madkursus.service.applications.CookingProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CookingProcessApiDelegateImpl implements CookingProcessApiDelegate {
    private final CookingProcessService service;
    private final CookingProcessRestMapper mapper;

    @Override
    public ResponseEntity<List<CookingProcessDTO>> getCookingProcesses(String search) {
        return ResponseEntity.ok(service.search(search).stream().map(mapper::toDto).toList());
    }

    @Override
    public ResponseEntity<CookingProcessDTO> getCookingProcess(UUID id) {
        return ResponseEntity.ok(mapper.toDto(service.get(id)));
    }
}
