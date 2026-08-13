package dk.jamesbabz.madkursus.service.ports;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import dk.jamesbabz.madkursus.service.models.ProductTemplate;

public interface ProductTemplatePort {
    List<ProductTemplate> search(String search, Boolean common);
    Optional<ProductTemplate> findById(UUID id);
}
