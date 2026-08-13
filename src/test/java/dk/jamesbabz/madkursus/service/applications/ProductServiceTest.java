package dk.jamesbabz.madkursus.service.applications;

import java.util.Optional;
import java.util.UUID;

import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import dk.jamesbabz.madkursus.service.models.Product;
import dk.jamesbabz.madkursus.service.models.ProductCategory;
import dk.jamesbabz.madkursus.service.models.Unit;
import dk.jamesbabz.madkursus.service.ports.ProductPort;
import dk.jamesbabz.madkursus.service.ports.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock ProductPort port;
    @Mock CurrentUserProvider currentUserProvider;
    @InjectMocks ProductService service;

    @Test
    void createsProduct() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(currentUserProvider.currentUserId()).thenReturn(userId);
        when(port.existsByUserIdAndNormalizedName(org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(port.save(any())).thenAnswer(call -> {
            Product product = call.getArgument(0);
            return new Product(id, product.userId(), product.name(), product.category(), product.defaultUnit());
        });
        Product result = service.create("Løg", ProductCategory.VEGETABLE, Unit.PIECE);
        assertThat(result.id()).isEqualTo(id);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.name()).isEqualTo("Løg");
    }

    @Test
    void retrievesProduct() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Product product = new Product(id, userId, "Mel", ProductCategory.DRY_GOODS, Unit.GRAM);
        when(currentUserProvider.currentUserId()).thenReturn(userId);
        when(port.findByIdAndUserId(id, userId)).thenReturn(Optional.of(product));
        assertThat(service.get(id)).isEqualTo(product);
    }

    @Test
    void unknownProductThrowsNotFound() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(currentUserProvider.currentUserId()).thenReturn(userId);
        when(port.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listsOnlyCurrentUsersProducts() {
        UUID userId = UUID.randomUUID();
        when(currentUserProvider.currentUserId()).thenReturn(userId);
        service.getAll();
        org.mockito.Mockito.verify(port).findAllByUserId(userId);
    }

    @Test
    void anotherUsersProductCannotBeUpdated() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(currentUserProvider.currentUserId()).thenReturn(userId);
        when(port.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(id, "Changed", ProductCategory.OTHER, Unit.PIECE))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(port, never()).save(any());
    }

    @Test
    void anotherUsersProductCannotBeDeleted() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(currentUserProvider.currentUserId()).thenReturn(userId);
        when(port.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ResourceNotFoundException.class);
        verify(port, never()).deleteByIdAndUserId(any(), any());
    }

    @Test
    void deletesOwnedProductUsingOwnerScopedPort() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Product product = new Product(id, userId, "Æg", ProductCategory.EGG, Unit.PIECE);
        when(currentUserProvider.currentUserId()).thenReturn(userId);
        when(port.findByIdAndUserId(id, userId)).thenReturn(Optional.of(product));

        service.delete(id);

        verify(port).deleteByIdAndUserId(id, userId);
    }
}
