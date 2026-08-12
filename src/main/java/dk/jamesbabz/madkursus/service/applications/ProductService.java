package dk.jamesbabz.madkursus.service.applications;

import java.util.List;
import java.util.UUID;

import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import dk.jamesbabz.madkursus.service.models.Product;
import dk.jamesbabz.madkursus.service.models.ProductCategory;
import dk.jamesbabz.madkursus.service.models.Unit;
import dk.jamesbabz.madkursus.service.ports.ProductPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductPort productPort;

    public Product create(String name, ProductCategory category, Unit defaultUnit) {
        return productPort.save(new Product(null, name, category, defaultUnit));
    }

    public Product get(UUID id) {
        return productPort.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    public List<Product> getAll() { return productPort.findAll(); }

    public Product update(UUID id, String name, ProductCategory category, Unit defaultUnit) {
        get(id);
        return productPort.save(new Product(id, name, category, defaultUnit));
    }

    public void delete(UUID id) {
        get(id);
        productPort.deleteById(id);
    }
}
