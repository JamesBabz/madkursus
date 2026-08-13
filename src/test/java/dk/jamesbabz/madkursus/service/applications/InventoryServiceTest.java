package dk.jamesbabz.madkursus.service.applications;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import dk.jamesbabz.madkursus.service.exceptions.InvalidInputException;
import dk.jamesbabz.madkursus.service.exceptions.ConflictException;
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
import static org.mockito.ArgumentMatchers.argThat;
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
    void cookingConsumesOnlyAvailableStockAndReportsShortageWithoutGoingNegative() {
        UUID userId=UUID.randomUUID(), productId=UUID.randomUUID(), itemId=UUID.randomUUID();
        Product product=product(productId,userId,"Oksekød",Unit.GRAM);
        when(currentUserProvider.currentUserId()).thenReturn(userId); when(productService.get(productId)).thenReturn(product);
        when(port.findByProductIdAndUserId(productId,userId)).thenReturn(Optional.of(new InventoryItem(itemId,product,new BigDecimal("300"))));

        InventoryService.Consumption result=service.consumeUpToAvailable(productId,new BigDecimal("400"));

        assertThat(result.deducted()).isEqualByComparingTo("300"); assertThat(result.shortage()).isEqualByComparingTo("100");
        verify(port).deleteByIdAndUserId(itemId,userId); verify(port,never()).save(any());
    }

    @Test
    void cookingSupportsHalfPieceDeduction() {
        UUID userId=UUID.randomUUID(), productId=UUID.randomUUID(), itemId=UUID.randomUUID(); Product product=product(productId,userId,"Løg",Unit.PIECE);
        when(currentUserProvider.currentUserId()).thenReturn(userId);when(productService.get(productId)).thenReturn(product);when(port.findByProductIdAndUserId(productId,userId)).thenReturn(Optional.of(new InventoryItem(itemId,product,new BigDecimal("2"))));when(port.save(any())).thenAnswer(c->c.getArgument(0));
        var result=service.consumeUpToAvailable(productId,new BigDecimal("0.5"));
        assertThat(result.shortage()).isZero();verify(port).save(argThat(i->i.quantity().compareTo(new BigDecimal("1.5"))==0));
    }

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
        when(productService.createFromTemplate(template.id(), template.name(), template.category(), template.defaultUnit(), template.defaultTrackingMode())).thenReturn(created);
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
        when(productService.createFromTemplate(template.id(), template.name(), template.category(), template.defaultUnit(), template.defaultTrackingMode())).thenReturn(existing);
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
        UUID id = UUID.randomUUID(); Product gram = product(id, UUID.randomUUID(), "Mel", Unit.GRAM);
        when(productService.get(id)).thenReturn(gram);
        assertThatThrownBy(() -> service.add(id, new BigDecimal("1.5")))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Quantity must be a whole number");
    }

    @Test
    void rejectsDecimalQuantityWhenEditingStock() {
        UUID user = UUID.randomUUID(), id = UUID.randomUUID(); Product gram = product(UUID.randomUUID(), user, "Mel", Unit.GRAM);
        when(currentUserProvider.currentUserId()).thenReturn(user);
        when(port.findByIdAndUserId(id, user)).thenReturn(Optional.of(new InventoryItem(id, gram, BigDecimal.TEN)));
        assertThatThrownBy(() -> service.setQuantity(id, new BigDecimal("2.01")))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Quantity must be a whole number");
    }

    @Test
    void purchaseRollbackSubtractsExactQuantity() {
        UUID userId = UUID.randomUUID(); Product product = product(UUID.randomUUID(), userId, "Æg", Unit.PIECE);
        UUID itemId = UUID.randomUUID();
        when(currentUserProvider.currentUserId()).thenReturn(userId);
        when(productService.get(product.id())).thenReturn(product);
        when(port.findByProductIdAndUserId(product.id(), userId))
                .thenReturn(Optional.of(new InventoryItem(itemId, product, new BigDecimal("16"))));
        when(port.save(any())).thenAnswer(call -> call.getArgument(0));

        service.removePurchasedQuantity(product.id(), new BigDecimal("10"));

        verify(port).save(new InventoryItem(itemId, product, new BigDecimal("6")));
    }

    @Test
    void purchaseRollbackRemovesZeroInventoryRow() {
        UUID userId = UUID.randomUUID(); Product product = product(UUID.randomUUID(), userId, "Æg", Unit.PIECE);
        UUID itemId = UUID.randomUUID();
        when(currentUserProvider.currentUserId()).thenReturn(userId);
        when(productService.get(product.id())).thenReturn(product);
        when(port.findByProductIdAndUserId(product.id(), userId))
                .thenReturn(Optional.of(new InventoryItem(itemId, product, BigDecimal.TEN)));

        service.removePurchasedQuantity(product.id(), BigDecimal.TEN);

        verify(port).deleteByIdAndUserId(itemId, userId);
    }

    @Test
    void purchaseRollbackCannotMakeInventoryNegative() {
        UUID userId = UUID.randomUUID(); Product product = product(UUID.randomUUID(), userId, "Æg", Unit.PIECE);
        UUID itemId = UUID.randomUUID();
        when(currentUserProvider.currentUserId()).thenReturn(userId);
        when(productService.get(product.id())).thenReturn(product);
        when(port.findByProductIdAndUserId(product.id(), userId))
                .thenReturn(Optional.of(new InventoryItem(itemId, product, new BigDecimal("4"))));

        assertThatThrownBy(() -> service.removePurchasedQuantity(product.id(), BigDecimal.TEN))
                .isInstanceOf(ConflictException.class);
        verify(port, never()).save(any()); verify(port, never()).deleteByIdAndUserId(any(), any());
    }

    @Test
    void presenceProductIsMarkedAvailableWithoutQuantityOrDuplicates() {
        UUID user = UUID.randomUUID(), productId = UUID.randomUUID();
        Product presence = new Product(productId, user, null, "Salt", ProductCategory.SPICE, Unit.GRAM,
                dk.jamesbabz.madkursus.service.models.InventoryTrackingMode.PRESENCE);
        when(productService.get(productId)).thenReturn(presence);
        when(currentUserProvider.currentUserId()).thenReturn(user);
        when(port.findByProductIdAndUserId(productId, user)).thenReturn(Optional.empty());
        when(port.save(any())).thenAnswer(call -> call.getArgument(0));
        InventoryItem created = service.add(productId, null);
        assertThat(created.quantity()).isNull();

        when(port.findByProductIdAndUserId(productId, user)).thenReturn(Optional.of(created));
        assertThat(service.add(productId, null)).isSameAs(created);
        verify(port, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    void unitDependentQuantityValidationAllowsOnlyHalfPieces() {
        UUID user = UUID.randomUUID(), pieceId = UUID.randomUUID(), gramId = UUID.randomUUID();
        Product piece = product(pieceId, user, "Æg", Unit.PIECE);
        Product gram = product(gramId, user, "Mel", Unit.GRAM);
        when(currentUserProvider.currentUserId()).thenReturn(user);
        when(productService.get(pieceId)).thenReturn(piece);
        when(productService.get(gramId)).thenReturn(gram);
        when(port.findByProductIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(port.save(any())).thenAnswer(call -> call.getArgument(0));

        assertThat(service.add(pieceId, new BigDecimal("0.5")).quantity()).isEqualByComparingTo("0.5");
        assertThat(service.add(pieceId, new BigDecimal("1.5")).quantity()).isEqualByComparingTo("1.5");
        assertThatThrownBy(() -> service.add(pieceId, new BigDecimal("1.25"))).isInstanceOf(InvalidInputException.class);
        assertThat(service.add(gramId, new BigDecimal("10")).quantity()).isEqualByComparingTo("10");
        assertThatThrownBy(() -> service.add(gramId, new BigDecimal("10.5"))).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void presenceTemplateAddsAvailabilityWithoutQuantity() {
        UUID user = UUID.randomUUID(), templateId = UUID.randomUUID(), productId = UUID.randomUUID();
        ProductTemplate salt = new ProductTemplate(templateId, "Salt", ProductCategory.SPICE, Unit.GRAM,
                dk.jamesbabz.madkursus.service.models.InventoryTrackingMode.PRESENCE, java.util.List.of(), true);
        Product product = new Product(productId, user, templateId, "Salt", ProductCategory.SPICE, Unit.GRAM,
                dk.jamesbabz.madkursus.service.models.InventoryTrackingMode.PRESENCE);
        when(templateService.get(templateId)).thenReturn(salt);
        when(productService.createFromTemplate(templateId, "Salt", ProductCategory.SPICE, Unit.GRAM,
                dk.jamesbabz.madkursus.service.models.InventoryTrackingMode.PRESENCE)).thenReturn(product);
        when(productService.get(productId)).thenReturn(product);
        when(currentUserProvider.currentUserId()).thenReturn(user);
        when(port.findByProductIdAndUserId(productId, user)).thenReturn(Optional.empty());
        when(port.save(any())).thenAnswer(call -> call.getArgument(0));

        InventoryItem result = service.addFromTemplate(templateId, null);

        assertThat(result.quantity()).isNull();
        assertThat(result.unit()).isEqualTo(Unit.GRAM);
    }

    private Product product(UUID id, UUID userId, String name, Unit unit) {
        return new Product(id, userId, name, ProductCategory.OTHER, unit);
    }
}
