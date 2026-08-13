package dk.jamesbabz.madkursus.service.applications;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import dk.jamesbabz.madkursus.service.exceptions.InvalidInputException;
import dk.jamesbabz.madkursus.service.models.InventoryItem;
import dk.jamesbabz.madkursus.service.models.Product;
import dk.jamesbabz.madkursus.service.models.ProductCategory;
import dk.jamesbabz.madkursus.service.models.ProductTemplate;
import dk.jamesbabz.madkursus.service.models.Unit;
import dk.jamesbabz.madkursus.service.ports.CurrentUserProvider;
import dk.jamesbabz.madkursus.service.ports.InventoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
    @Mock InventoryPort port;
    @Mock ProductService productService;
    @Mock ProductTemplateService templateService;
    @Mock CurrentUserProvider currentUserProvider;
    @InjectMocks InventoryService service;

    @Test
    void addsOwnedProductAndDerivesUnitFromIt() {
        UUID userId = UUID.randomUUID(); UUID productId = UUID.randomUUID();
        Product product = product(productId, userId, "Æg", Unit.PIECE);
        when(currentUserProvider.currentUserId()).thenReturn(userId);
        when(productService.get(productId)).thenReturn(product);
        when(port.findByProductIdAndUserId(productId, userId)).thenReturn(Optional.empty());
        when(port.save(any())).thenAnswer(call -> call.getArgument(0));

        InventoryItem result = service.add(productId, BigDecimal.TEN);

        assertThat(result.product()).isEqualTo(product);
        assertThat(result.quantity()).isEqualByComparingTo("10");
        assertThat(result.unit()).isEqualTo(Unit.PIECE);
    }

    @Test
    void addingAgainIncreasesExistingQuantityWithoutCreatingAnotherRow() {
        UUID userId = UUID.randomUUID(); UUID productId = UUID.randomUUID(); UUID itemId = UUID.randomUUID();
        Product product = product(productId, userId, "Ris", Unit.GRAM);
        when(currentUserProvider.currentUserId()).thenReturn(userId);
        when(productService.get(productId)).thenReturn(product);
        when(port.findByProductIdAndUserId(productId, userId))
                .thenReturn(Optional.of(new InventoryItem(itemId, product, new BigDecimal("750"))));
        when(port.save(any())).thenAnswer(call -> call.getArgument(0));

        InventoryItem result = service.add(productId, new BigDecimal("500"));

        assertThat(result.id()).isEqualTo(itemId);
        assertThat(result.quantity()).isEqualByComparingTo("1250");
    }

    @Test
    void templateCreatesProductThenInventoryAtomicallyThroughServiceTransaction() {
        UUID templateId = UUID.randomUUID(); UUID productId = UUID.randomUUID(); UUID userId = UUID.randomUUID();
        ProductTemplate template = new ProductTemplate(templateId, "Hakket oksekød", ProductCategory.MEAT,
                Unit.GRAM, java.util.List.of("oksefars"), false);
        Product created = product(productId, userId, template.name(), template.defaultUnit());
        when(templateService.get(templateId)).thenReturn(template);
        when(productService.findEquivalent(template.name())).thenReturn(Optional.empty());
        when(productService.create(template.name(), template.category(), template.defaultUnit())).thenReturn(created);
        when(currentUserProvider.currentUserId()).thenReturn(userId);
        when(productService.get(productId)).thenReturn(created);
        when(port.findByProductIdAndUserId(productId, userId)).thenReturn(Optional.empty());
        when(port.save(any())).thenAnswer(call -> call.getArgument(0));

        InventoryItem result = service.addFromTemplate(templateId, new BigDecimal("500"));

        assertThat(result.product()).isEqualTo(created);
        assertThat(result.quantity()).isEqualByComparingTo("500");
    }

    @Test
    void templateReusesEquivalentOwnedProduct() {
        UUID templateId = UUID.randomUUID(); UUID userId = UUID.randomUUID(); Product existing = product(UUID.randomUUID(), userId, "Sojasauce", Unit.MILLILITER);
        ProductTemplate template = new ProductTemplate(templateId, existing.name(), ProductCategory.SAUCE_CONDIMENT,
                Unit.MILLILITER, java.util.List.of("soya"), false);
        when(templateService.get(templateId)).thenReturn(template);
        when(productService.findEquivalent(template.name())).thenReturn(Optional.of(existing));
        when(currentUserProvider.currentUserId()).thenReturn(userId);
        when(productService.get(existing.id())).thenReturn(existing);
        when(port.findByProductIdAndUserId(existing.id(), userId)).thenReturn(Optional.empty());
        when(port.save(any())).thenAnswer(call -> call.getArgument(0));

        service.addFromTemplate(templateId, BigDecimal.ONE);

        verify(productService, never()).create(any(), any(), any());
    }

    @Test
    void crossUserInventoryLookupIsNotFound() {
        UUID userId = UUID.randomUUID(); UUID id = UUID.randomUUID();
        when(currentUserProvider.currentUserId()).thenReturn(userId);
        when(port.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void zeroQuantityRemovesInventoryButNotProduct() {
        UUID userId = UUID.randomUUID(); UUID id = UUID.randomUUID();
        Product product = product(UUID.randomUUID(), userId, "Mælk", Unit.MILLILITER);
        when(currentUserProvider.currentUserId()).thenReturn(userId);
        when(port.findByIdAndUserId(id, userId)).thenReturn(Optional.of(new InventoryItem(id, product, BigDecimal.TEN)));

        service.setQuantity(id, BigDecimal.ZERO);

        verify(port).deleteByIdAndUserId(id, userId);
        verify(productService, never()).delete(any());
    }

    @Test
    void rejectsDecimalQuantityWhenAddingStock() {
        assertThatThrownBy(() -> service.add(UUID.randomUUID(), new BigDecimal("1.5")))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Quantity must be a whole number");
    }

    @Test
    void rejectsDecimalQuantityWhenEditingStock() {
        assertThatThrownBy(() -> service.setQuantity(UUID.randomUUID(), new BigDecimal("2.01")))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Quantity must be a whole number");
    }

    private Product product(UUID id, UUID userId, String name, Unit unit) {
        return new Product(id, userId, name, ProductCategory.OTHER, unit);
    }
}
