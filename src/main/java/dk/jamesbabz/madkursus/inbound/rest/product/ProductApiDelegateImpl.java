package dk.jamesbabz.madkursus.inbound.rest.product;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import dk.jamesbabz.madkursus.inbound.rest.ProductApiDelegate;
import dk.jamesbabz.madkursus.inbound.rest.dto.CreateProductDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.ProductDTO;
import dk.jamesbabz.madkursus.service.applications.ProductService;
import dk.jamesbabz.madkursus.service.applications.ProductTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductApiDelegateImpl implements ProductApiDelegate {
    private final ProductService service;
    private final ProductRestMapper mapper;
    private final ProductTemplateService templateService;

    public ResponseEntity<ProductDTO> createProductFromTemplate(UUID templateId) {
        ProductDTO result = mapper.toDto(templateService.addToProducts(templateId));
        return ResponseEntity.created(URI.create("/v1/products/" + result.getId())).body(result);
    }

    public ResponseEntity<ProductDTO> createProduct(CreateProductDTO request) {
        ProductDTO result = mapper.toDto(service.create(request.getName(), mapper.toCategory(request.getCategory()),
                mapper.toUnit(request.getDefaultUnit())));
        return ResponseEntity.created(URI.create("/v1/products/" + result.getId())).body(result);
    }

    public ResponseEntity<List<ProductDTO>> getProducts() {
        return ResponseEntity.ok(service.getAll().stream().map(mapper::toDto).toList());
    }

    public ResponseEntity<ProductDTO> getProduct(UUID id) { return ResponseEntity.ok(mapper.toDto(service.get(id))); }

    public ResponseEntity<ProductDTO> updateProduct(UUID id, CreateProductDTO request) {
        return ResponseEntity.ok(mapper.toDto(service.update(id, request.getName(),
                mapper.toCategory(request.getCategory()), mapper.toUnit(request.getDefaultUnit()),
                mapper.toTrackingMode(request.getInventoryTrackingMode()))));
    }

    public ResponseEntity<Void> deleteProduct(UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
