package dk.jamesbabz.madkursus.outbound.producttemplate;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import dk.jamesbabz.madkursus.outbound.producttemplate.details.ProductTemplateJpaRepository;
import dk.jamesbabz.madkursus.outbound.producttemplate.mappers.ProductTemplateEntityMapper;
import dk.jamesbabz.madkursus.service.models.ProductTemplate;
import dk.jamesbabz.madkursus.service.ports.ProductTemplatePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class ProductTemplateAdapterImpl implements ProductTemplatePort {
    private final ProductTemplateJpaRepository repository;
    private final ProductTemplateEntityMapper mapper;
    public List<ProductTemplate> search(String search, Boolean common) {
        String normalized = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return repository.search(normalized, common).stream().map(mapper::toModel).toList();
    }
    public Optional<ProductTemplate> findById(UUID id) { return repository.findById(id).map(mapper::toModel); }
}
