package dk.jamesbabz.madkursus.service.applications;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import dk.jamesbabz.madkursus.service.exceptions.ConflictException;
import dk.jamesbabz.madkursus.service.exceptions.InvalidInputException;
import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.ports.CurrentUserProvider;
import dk.jamesbabz.madkursus.service.ports.ShoppingListPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShoppingListServiceTest {
    @Mock ShoppingListPort port;
    @Mock ProductService productService;
    @Mock ProductTemplateService templateService;
    @Mock InventoryService inventoryService;
    @Mock CurrentUserProvider currentUserProvider;
    @InjectMocks ShoppingListService service;

    @Test void addsOwnedProductAndUsesItsUnit() {
        UUID user = UUID.randomUUID(); Product product = product(user, "Æg", Unit.PIECE);
        when(productService.get(product.id())).thenReturn(product); when(currentUserProvider.currentUserId()).thenReturn(user);
        when(port.findActiveByProductIdAndUserId(product.id(), user)).thenReturn(Optional.empty()); when(port.save(any())).thenAnswer(i -> i.getArgument(0));
        ShoppingListItem result = service.add(product.id(), BigDecimal.TEN);
        assertThat(result.quantity()).isEqualByComparingTo("10"); assertThat(result.unit()).isEqualTo(Unit.PIECE);
    }

    @Test void repeatedAddIncreasesOneActiveItem() {
        UUID user = UUID.randomUUID(); Product product = product(user, "Æg", Unit.PIECE); UUID id = UUID.randomUUID();
        when(productService.get(product.id())).thenReturn(product); when(currentUserProvider.currentUserId()).thenReturn(user);
        when(port.findActiveByProductIdAndUserId(product.id(), user)).thenReturn(Optional.of(item(id, user, product, "6", false)));
        when(port.save(any())).thenAnswer(i -> i.getArgument(0));
        ShoppingListItem result = service.add(product.id(), new BigDecimal("4"));
        assertThat(result.id()).isEqualTo(id); assertThat(result.quantity()).isEqualByComparingTo("10");
    }

    @Test void addFromTemplateCreatesProductWhenMissing() {
        UUID user = UUID.randomUUID(), templateId = UUID.randomUUID(); Product product = product(user, "Hakket oksekød", Unit.GRAM);
        ProductTemplate template = new ProductTemplate(templateId, product.name(), ProductCategory.MEAT, Unit.GRAM, java.util.List.of("oksefars"), false);
        when(templateService.get(templateId)).thenReturn(template);
        when(productService.createFromTemplate(template.id(), template.name(), template.category(), template.defaultUnit(), template.defaultTrackingMode())).thenReturn(product);
        when(productService.get(product.id())).thenReturn(product); when(currentUserProvider.currentUserId()).thenReturn(user);
        when(port.findActiveByProductIdAndUserId(product.id(), user)).thenReturn(Optional.empty()); when(port.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.addFromTemplate(templateId, new BigDecimal("500")).product()).isEqualTo(product);
    }

    @Test void ensureAtLeastAccountsForExistingActiveQuantityAndIsRepeatSafe() {
        UUID user=UUID.randomUUID(), templateId=UUID.randomUUID();Product product=product(user,"Oksekød",Unit.GRAM);ProductTemplate template=new ProductTemplate(templateId,product.name(),ProductCategory.MEAT,Unit.GRAM,java.util.List.of(),false);ShoppingListItem active=item(UUID.randomUUID(),user,product,"300",false);
        when(templateService.get(templateId)).thenReturn(template);when(productService.createFromTemplate(template.id(),template.name(),template.category(),template.defaultUnit(),template.defaultTrackingMode())).thenReturn(product);when(currentUserProvider.currentUserId()).thenReturn(user);when(port.findActiveByProductIdAndUserId(product.id(),user)).thenReturn(Optional.of(active));when(productService.get(product.id())).thenReturn(product);when(port.save(any())).thenAnswer(i->i.getArgument(0));
        assertThat(service.ensureAtLeastFromTemplate(templateId,new BigDecimal("500")).quantity()).isEqualByComparingTo("500");
        ShoppingListItem enough=item(active.id(),user,product,"500",false);when(port.findActiveByProductIdAndUserId(product.id(),user)).thenReturn(Optional.of(enough));
        assertThat(service.ensureAtLeastFromTemplate(templateId,new BigDecimal("500"))).isSameAs(enough);
    }

    @Test void ensureAtLeastDoesNotDuplicatePresenceRestock() {
        UUID user=UUID.randomUUID(),templateId=UUID.randomUUID();Product product=new Product(UUID.randomUUID(),user,templateId,"Salt",ProductCategory.SPICE,Unit.GRAM,InventoryTrackingMode.PRESENCE);ProductTemplate template=new ProductTemplate(templateId,"Salt",ProductCategory.SPICE,Unit.GRAM,InventoryTrackingMode.PRESENCE,java.util.List.of(),false);ShoppingListItem active=new ShoppingListItem(UUID.randomUUID(),user,product,null,false,null);
        when(templateService.get(templateId)).thenReturn(template);when(productService.createFromTemplate(any(),any(),any(),any(),any())).thenReturn(product);when(currentUserProvider.currentUserId()).thenReturn(user);when(port.findActiveByProductIdAndUserId(product.id(),user)).thenReturn(Optional.of(active));
        assertThat(service.ensureAtLeastFromTemplate(templateId,null)).isSameAs(active);verify(port,never()).save(any());
    }

    @Test void purchaseUpdatesInventoryOnceAndRecordsTimestamp() {
        UUID user = UUID.randomUUID(), id = UUID.randomUUID(); Product product = product(user, "Mælk", Unit.MILLILITER);
        ShoppingListItem item = item(id, user, product, "1000", false); owned(item); when(port.save(any())).thenAnswer(i -> i.getArgument(0));
        ShoppingListItem result = service.purchase(id);
        verify(inventoryService).add(product.id(), item.quantity()); assertThat(result.purchased()).isTrue(); assertThat(result.purchasedAt()).isNotNull();
    }

    @Test void purchasingAlreadyPurchasedItemDoesNotDoubleAdd() {
        UUID user = UUID.randomUUID(), id = UUID.randomUUID(); Product product = product(user, "Æg", Unit.PIECE);
        ShoppingListItem item = item(id, user, product, "10", true); owned(item);
        assertThat(service.purchase(id)).isSameAs(item); verifyNoInteractions(inventoryService); verify(port, never()).save(any());
    }

    @Test void undoReversesExactInventoryQuantity() {
        UUID user = UUID.randomUUID(), id = UUID.randomUUID(); Product product = product(user, "Smør", Unit.GRAM);
        ShoppingListItem item = item(id, user, product, "250", true); owned(item); when(port.save(any())).thenAnswer(i -> i.getArgument(0));
        ShoppingListItem result = service.undoPurchase(id);
        verify(inventoryService).removePurchasedQuantity(product.id(), item.quantity()); assertThat(result.purchased()).isFalse(); assertThat(result.purchasedAt()).isNull();
    }

    @Test void invalidUndoDoesNotTouchInventory() {
        UUID user = UUID.randomUUID(), id = UUID.randomUUID(); ShoppingListItem item = item(id, user, product(user, "Ris", Unit.GRAM), "500", false); owned(item);
        assertThatThrownBy(() -> service.undoPurchase(id)).isInstanceOf(ConflictException.class); verifyNoInteractions(inventoryService);
    }

    @Test void undoMergesWithNewActiveItemForSameProduct() {
        UUID user = UUID.randomUUID(), purchasedId = UUID.randomUUID(), activeId = UUID.randomUUID();
        Product product = product(user, "Æg", Unit.PIECE);
        ShoppingListItem purchased = item(purchasedId, user, product, "10", true);
        ShoppingListItem active = item(activeId, user, product, "4", false);
        owned(purchased); when(port.findActiveByProductIdAndUserId(product.id(), user)).thenReturn(Optional.of(active));
        when(port.save(any())).thenAnswer(i -> i.getArgument(0));

        ShoppingListItem result = service.undoPurchase(purchasedId);

        assertThat(result.id()).isEqualTo(activeId); assertThat(result.quantity()).isEqualByComparingTo("14");
        verify(port).deleteByIdAndUserId(purchasedId, user);
    }

    @Test void purchasedItemCannotBeEditedOrDeleted() {
        UUID user = UUID.randomUUID(), id = UUID.randomUUID(); ShoppingListItem item = item(id, user, product(user, "Ris", Unit.GRAM), "500", true);
        owned(item); assertThatThrownBy(() -> service.update(id, BigDecimal.TEN)).isInstanceOf(ConflictException.class);
        owned(item); assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ConflictException.class); verify(port, never()).deleteByIdAndUserId(any(), any());
    }

    @Test void unpurchasedItemCanBeEditedAndDeleted() {
        UUID user = UUID.randomUUID(), id = UUID.randomUUID(); ShoppingListItem item = item(id, user, product(user, "Ris", Unit.GRAM), "500", false);
        owned(item); when(port.save(any())).thenAnswer(i -> i.getArgument(0)); assertThat(service.update(id, new BigDecimal("750")).quantity()).isEqualByComparingTo("750");
        owned(item); service.delete(id); verify(port).deleteByIdAndUserId(id, user);
    }

    @Test void crossUserItemIsNotFound() {
        UUID user = UUID.randomUUID(), id = UUID.randomUUID(); when(currentUserProvider.currentUserId()).thenReturn(user);
        when(port.findByIdAndUserIdForUpdate(id, user)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.purchase(id)).isInstanceOf(ResourceNotFoundException.class); verifyNoInteractions(inventoryService);
    }

    @Test void fractionalQuantitiesAreRejected() {
        UUID id = UUID.randomUUID(), user = UUID.randomUUID(); Product gram = product(user, "Mel", Unit.GRAM);
        when(productService.get(id)).thenReturn(gram);
        assertThatThrownBy(() -> service.add(id, new BigDecimal("1.5"))).isInstanceOf(InvalidInputException.class);
        ShoppingListItem item = item(id, user, gram, "2", false); owned(item);
        assertThatThrownBy(() -> service.update(id, new BigDecimal("2.25"))).isInstanceOf(InvalidInputException.class);
    }

    @Test void clearPurchasedOnlyDeletesShoppingRows() {
        UUID user = UUID.randomUUID(); when(currentUserProvider.currentUserId()).thenReturn(user); service.clearPurchased();
        verify(port).deletePurchasedByUserId(user); verifyNoInteractions(inventoryService);
    }

    @Test void presenceRestockHasNoFakeQuantityAndPurchaseMarksAvailable() {
        UUID user = UUID.randomUUID();
        Product presence = new Product(UUID.randomUUID(), user, null, "Salt", ProductCategory.SPICE, Unit.GRAM,
                InventoryTrackingMode.PRESENCE);
        when(productService.get(presence.id())).thenReturn(presence);
        when(currentUserProvider.currentUserId()).thenReturn(user);
        when(port.findActiveByProductIdAndUserId(presence.id(), user)).thenReturn(Optional.empty());
        when(port.save(any())).thenAnswer(i -> i.getArgument(0));
        ShoppingListItem restock = service.add(presence.id(), null);
        assertThat(restock.quantity()).isNull();

        ShoppingListItem stored = new ShoppingListItem(UUID.randomUUID(), user, presence, null, false, null);
        owned(stored); when(inventoryService.markAvailable(presence.id())).thenReturn(true);
        ShoppingListItem purchased = service.purchase(stored.id());
        assertThat(purchased.inventoryWasPresent()).isFalse();
        verify(inventoryService).markAvailable(presence.id());
        verify(inventoryService, never()).add(any(), any());
    }

    @Test void presenceUndoOnlyRemovesAvailabilityCreatedByPurchase() {
        UUID user = UUID.randomUUID();
        Product presence = new Product(UUID.randomUUID(), user, null, "Salt", ProductCategory.SPICE, Unit.GRAM,
                InventoryTrackingMode.PRESENCE);
        ShoppingListItem addedByPurchase = new ShoppingListItem(UUID.randomUUID(), user, presence, null, true,
                Instant.now(), false);
        owned(addedByPurchase); when(port.findActiveByProductIdAndUserId(presence.id(), user)).thenReturn(Optional.empty());
        when(port.save(any())).thenAnswer(i -> i.getArgument(0));
        service.undoPurchase(addedByPurchase.id());
        verify(inventoryService).removeAvailability(presence.id());

        ShoppingListItem alreadyPresent = new ShoppingListItem(UUID.randomUUID(), user, presence, null, true,
                Instant.now(), true);
        owned(alreadyPresent); when(port.findActiveByProductIdAndUserId(presence.id(), user)).thenReturn(Optional.empty());
        service.undoPurchase(alreadyPresent.id());
        verify(inventoryService, org.mockito.Mockito.times(1)).removeAvailability(presence.id());
    }

    private void owned(ShoppingListItem item) {
        when(currentUserProvider.currentUserId()).thenReturn(item.userId());
        when(port.findByIdAndUserIdForUpdate(item.id(), item.userId())).thenReturn(Optional.of(item));
    }
    private Product product(UUID user, String name, Unit unit) { return new Product(UUID.randomUUID(), user, name, ProductCategory.OTHER, unit); }
    private ShoppingListItem item(UUID id, UUID user, Product product, String quantity, boolean purchased) {
        return new ShoppingListItem(id, user, product, new BigDecimal(quantity), purchased, purchased ? Instant.now() : null);
    }
}
