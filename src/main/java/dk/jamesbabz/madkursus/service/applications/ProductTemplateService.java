package dk.jamesbabz.madkursus.service.applications;

import java.util.List;
import java.util.UUID;
import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import dk.jamesbabz.madkursus.service.models.Product;
import dk.jamesbabz.madkursus.service.models.ProductTemplate;
import dk.jamesbabz.madkursus.service.ports.ProductTemplatePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class ProductTemplateService {
    private final ProductTemplatePort port;
    private final ProductService productService;
    public List<ProductTemplate> search(String search, Boolean common) { return port.search(search, common); }
    public ProductTemplate get(UUID id) { return port.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product template", id)); }
    public Product addToProducts(UUID id) {
        ProductTemplate template = get(id);
        return productService.create(template.name(), template.category(), template.defaultUnit());
    }
}
