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
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ProductAdapterImpl implements ProductPort {
    private final ProductJpaRepository repository;
    private final ProductEntityMapper mapper;

    public Product save(Product product) { return mapper.toModel(repository.save(mapper.toEntity(product))); }
    public Optional<Product> findByIdAndUserId(UUID id, UUID userId) {
        return repository.findByIdAndUserId(id, userId).map(mapper::toModel);
    }
    public List<Product> findAllByUserId(UUID userId) {
        return repository.findAllByUserIdOrderByNameAsc(userId).stream().map(mapper::toModel).toList();
    }
    @Transactional
    public void deleteByIdAndUserId(UUID id, UUID userId) { repository.deleteByIdAndUserId(id, userId); }
}
