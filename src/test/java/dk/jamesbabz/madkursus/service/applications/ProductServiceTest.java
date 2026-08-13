package dk.jamesbabz.madkursus.service.applications;

import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import dk.jamesbabz.madkursus.service.models.Product;
import dk.jamesbabz.madkursus.service.models.ProductCategory;
import dk.jamesbabz.madkursus.service.models.Unit;
import dk.jamesbabz.madkursus.service.ports.ProductPort;
import dk.jamesbabz.madkursus.service.ports.CurrentUserProvider;
import dk.jamesbabz.madkursus.service.ports.InventoryPort;
import dk.jamesbabz.madkursus.service.models.InventoryTrackingMode;
import dk.jamesbabz.madkursus.service.models.InventoryItem;
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
    @Mock InventoryPort inventoryPort;
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

    @Test
    void templateIdentityIsPreferredAndNewTemplateProductStoresOrigin() {
        UUID userId = UUID.randomUUID(), templateId = UUID.randomUUID();
        when(currentUserProvider.currentUserId()).thenReturn(userId);
        Product existing = new Product(UUID.randomUUID(), userId, templateId, "Customized", ProductCategory.OTHER,
                Unit.PIECE, InventoryTrackingMode.PRESENCE);
        when(port.findByUserIdAndSourceTemplateId(userId, templateId)).thenReturn(Optional.of(existing));
        assertThat(service.createFromTemplate(templateId, "Æg", ProductCategory.EGG, Unit.PIECE, InventoryTrackingMode.QUANTITY)).isEqualTo(existing);
        verify(port, never()).findByUserIdAndNormalizedName(any(), any());

        UUID otherTemplate = UUID.randomUUID();
        when(port.findByUserIdAndSourceTemplateId(userId, otherTemplate)).thenReturn(Optional.empty());
        when(port.findByUserIdAndNormalizedName(userId, "Salt")).thenReturn(Optional.empty());
        when(port.save(any())).thenAnswer(call -> call.getArgument(0));
        Product created = service.createFromTemplate(otherTemplate, "Salt", ProductCategory.SPICE, Unit.GRAM, InventoryTrackingMode.PRESENCE);
        assertThat(created.sourceTemplateId()).isEqualTo(otherTemplate);
        assertThat(created.inventoryTrackingMode()).isEqualTo(InventoryTrackingMode.PRESENCE);
    }

    @Test
    void normalizedNameFallbackStillReusesLegacyProduct() {
        UUID userId = UUID.randomUUID(), templateId = UUID.randomUUID();
        Product legacy = new Product(UUID.randomUUID(), userId, "Salt", ProductCategory.SPICE, Unit.GRAM);
        when(currentUserProvider.currentUserId()).thenReturn(userId);
        when(port.findByUserIdAndSourceTemplateId(userId, templateId)).thenReturn(Optional.empty());
        when(port.findByUserIdAndNormalizedName(userId, "Salt")).thenReturn(Optional.of(legacy));
        assertThat(service.createFromTemplate(templateId, "Salt", ProductCategory.SPICE, Unit.GRAM, InventoryTrackingMode.PRESENCE)).isEqualTo(legacy);
        assertThat(legacy.sourceTemplateId()).isNull();
    }

    @Test
    void trackingModeTransitionsInventoryDeliberately() {
        UUID userId = UUID.randomUUID(), productId = UUID.randomUUID(), inventoryId = UUID.randomUUID();
        Product quantityProduct = new Product(productId, userId, null, "Salt", ProductCategory.SPICE, Unit.GRAM,
                InventoryTrackingMode.QUANTITY);
        when(currentUserProvider.currentUserId()).thenReturn(userId);
        when(port.findByIdAndUserId(productId, userId)).thenReturn(Optional.of(quantityProduct));
        when(inventoryPort.findByProductIdAndUserId(productId, userId))
                .thenReturn(Optional.of(new InventoryItem(inventoryId, quantityProduct, BigDecimal.TEN)));
        when(port.save(any())).thenAnswer(call -> call.getArgument(0));

        Product presence = service.update(productId, "Salt", ProductCategory.SPICE, Unit.GRAM, InventoryTrackingMode.PRESENCE);
        assertThat(presence.inventoryTrackingMode()).isEqualTo(InventoryTrackingMode.PRESENCE);
        verify(inventoryPort).save(new InventoryItem(inventoryId, quantityProduct, null));

        Product presenceStored = new Product(productId, userId, null, "Salt", ProductCategory.SPICE, Unit.GRAM,
                InventoryTrackingMode.PRESENCE);
        when(port.findByIdAndUserId(productId, userId)).thenReturn(Optional.of(presenceStored));
        when(inventoryPort.findByProductIdAndUserId(productId, userId))
                .thenReturn(Optional.of(new InventoryItem(inventoryId, presenceStored, null)));
        service.update(productId, "Salt", ProductCategory.SPICE, Unit.GRAM, InventoryTrackingMode.QUANTITY);
        verify(inventoryPort).deleteByIdAndUserId(inventoryId, userId);
    }
}
