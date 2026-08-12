package dk.jamesbabz.madkursus.outbound.product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dk.jamesbabz.madkursus.outbound.product.details.ProductJpaRepository;
import dk.jamesbabz.madkursus.outbound.product.mappers.ProductEntityMapper;
import dk.jamesbabz.madkursus.service.models.Product;
import dk.jamesbabz.madkursus.service.ports.ProductPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductAdapterImpl implements ProductPort {
    private final ProductJpaRepository repository;
    private final ProductEntityMapper mapper;

    public Product save(Product product) { return mapper.toModel(repository.save(mapper.toEntity(product))); }
    public Optional<Product> findById(UUID id) { return repository.findById(id).map(mapper::toModel); }
    public List<Product> findAll() { return repository.findAll().stream().map(mapper::toModel).toList(); }
    public void deleteById(UUID id) { repository.deleteById(id); }
}
