package dk.jamesbabz.madkursus.inbound.rest.producttemplate;

import java.util.List;
import java.util.UUID;
import dk.jamesbabz.madkursus.inbound.rest.ProductTemplateApiDelegate;
import dk.jamesbabz.madkursus.inbound.rest.dto.ProductTemplateDTO;
import dk.jamesbabz.madkursus.service.applications.ProductTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class ProductTemplateApiDelegateImpl implements ProductTemplateApiDelegate {
    private final ProductTemplateService service;
    private final ProductTemplateRestMapper mapper;
    public ResponseEntity<List<ProductTemplateDTO>> getProductTemplates(String search, Boolean common) {
        return ResponseEntity.ok(service.search(search, common).stream().map(mapper::toDto).toList());
    }
    public ResponseEntity<ProductTemplateDTO> getProductTemplate(UUID id) {
        return ResponseEntity.ok(mapper.toDto(service.get(id)));
    }
}
