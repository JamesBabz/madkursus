package dk.jamesbabz.madkursus.service.applications;

import java.util.Optional;
import java.util.UUID;

import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import dk.jamesbabz.madkursus.service.models.Product;
import dk.jamesbabz.madkursus.service.models.ProductCategory;
import dk.jamesbabz.madkursus.service.models.Unit;
import dk.jamesbabz.madkursus.service.ports.ProductPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock ProductPort port;
    @InjectMocks ProductService service;

    @Test
    void createsProduct() {
        UUID id = UUID.randomUUID();
        when(port.save(any())).thenAnswer(call -> {
            Product product = call.getArgument(0);
            return new Product(id, product.name(), product.category(), product.defaultUnit());
        });
        Product result = service.create("Løg", ProductCategory.VEGETABLE, Unit.PIECE);
        assertThat(result.id()).isEqualTo(id);
        assertThat(result.name()).isEqualTo("Løg");
    }

    @Test
    void retrievesProduct() {
        UUID id = UUID.randomUUID();
        Product product = new Product(id, "Mel", ProductCategory.DRY_GOODS, Unit.GRAM);
        when(port.findById(id)).thenReturn(Optional.of(product));
        assertThat(service.get(id)).isEqualTo(product);
    }

    @Test
    void unknownProductThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(port.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(ResourceNotFoundException.class);
    }
}
