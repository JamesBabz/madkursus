package dk.jamesbabz.madkursus.service.applications;

import java.math.BigDecimal;
import java.util.UUID;

import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import dk.jamesbabz.madkursus.service.models.InventoryItem;
import dk.jamesbabz.madkursus.service.models.Product;
import dk.jamesbabz.madkursus.service.models.ProductCategory;
import dk.jamesbabz.madkursus.service.models.Unit;
import dk.jamesbabz.madkursus.service.ports.InventoryPort;
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
class InventoryServiceTest {
    @Mock InventoryPort port;
    @Mock ProductService productService;
    @InjectMocks InventoryService service;

    @Test
    void createsInventoryItem() {
        UUID productId = UUID.randomUUID();
        Product product = new Product(productId, "Løg", ProductCategory.VEGETABLE, Unit.PIECE);
        when(productService.get(productId)).thenReturn(product);
        when(port.save(any())).thenAnswer(call -> call.getArgument(0));
        InventoryItem result = service.create(productId, BigDecimal.valueOf(4), Unit.PIECE);
        assertThat(result.product()).isEqualTo(product);
        assertThat(result.quantity()).isEqualByComparingTo("4");
    }

    @Test
    void unknownProductIsPropagated() {
        UUID id = UUID.randomUUID();
        when(productService.get(id)).thenThrow(new ResourceNotFoundException("Product", id));
        assertThatThrownBy(() -> service.create(id, BigDecimal.ONE, Unit.PIECE))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
