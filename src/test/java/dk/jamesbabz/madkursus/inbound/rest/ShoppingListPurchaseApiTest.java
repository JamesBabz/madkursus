package dk.jamesbabz.madkursus.inbound.rest;

import dk.jamesbabz.madkursus.inbound.rest.product.ProductRestMapper;
import dk.jamesbabz.madkursus.inbound.rest.shoppinglist.*;
import dk.jamesbabz.madkursus.inbound.security.SecurityConfig;
import dk.jamesbabz.madkursus.service.applications.*;
import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.ports.*;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Real MVC mapping, shopping purchase, and inventory addition; only persistence ports are mocked.
@WebMvcTest(ShoppingListApiController.class)
@Import({SecurityConfig.class, ShoppingListApiDelegateImpl.class, ShoppingListRestMapper.class,
        ProductRestMapper.class, ShoppingListService.class, InventoryService.class})
class ShoppingListPurchaseApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean ShoppingListPort shopping;
    @MockitoBean InventoryPort inventory;
    @MockitoBean CurrentUserProvider currentUser;
    @MockitoBean ProductService products;
    @MockitoBean ProductTemplateService templates;
    @MockitoBean InventoryAvailabilityService availability;
    @MockitoBean UserDetailsService users;
    final UUID userId = UUID.randomUUID(), id = UUID.randomUUID();
    Product product;
    AtomicReference<ShoppingListItem> stored;

    @BeforeEach void setup() {
        product = new Product(UUID.randomUUID(), userId, "Æg", ProductCategory.EGG, Unit.PIECE);
        stored = new AtomicReference<>(new ShoppingListItem(id, userId, product, new BigDecimal("6"), false, null));
        when(currentUser.currentUserId()).thenReturn(userId);
        when(products.get(product.id())).thenReturn(product);
        when(shopping.findByIdAndUserIdForUpdate(id, userId)).thenAnswer(call -> Optional.of(stored.get()));
        when(shopping.save(any())).thenAnswer(call -> {stored.set(call.getArgument(0)); return stored.get();});
        when(inventory.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test void editedQuantityAddsTenToExistingInventoryAndRetryDoesNotAddTwice() throws Exception {
        when(inventory.findByProductIdAndUserId(product.id(), userId)).thenReturn(Optional.of(
                new InventoryItem(UUID.randomUUID(), product, new BigDecimal("4"))));
        purchase("{\"quantity\":10}").andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(10)).andExpect(jsonPath("$.purchased").value(true));
        purchase("{\"quantity\":12}").andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(10));
        var captor = org.mockito.ArgumentCaptor.forClass(InventoryItem.class);
        verify(inventory, times(1)).save(captor.capture());
        assertThat(captor.getValue().quantity()).isEqualByComparingTo("14");
        verify(shopping, times(1)).save(any());
    }

    @Test void editedQuantityCreatesInventoryWithTen() throws Exception {
        purchase("{\"quantity\":10}").andExpect(status().isOk());
        var captor = org.mockito.ArgumentCaptor.forClass(InventoryItem.class);
        verify(inventory).save(captor.capture());
        assertThat(captor.getValue().quantity()).isEqualByComparingTo("10");
    }

    @Test void existingBodylessPurchaseStillUsesStoredQuantity() throws Exception {
        purchase(null).andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(6));
        var captor = org.mockito.ArgumentCaptor.forClass(InventoryItem.class);
        verify(inventory).save(captor.capture());
        assertThat(captor.getValue().quantity()).isEqualByComparingTo("6");
    }

    @Test void presencePurchaseMarksAvailableWithoutInventingQuantity() throws Exception {
        product = new Product(product.id(), userId, null, "Salt", ProductCategory.SPICE, Unit.GRAM, InventoryTrackingMode.PRESENCE);
        when(products.get(product.id())).thenReturn(product);
        stored.set(new ShoppingListItem(id, userId, product, null, false, null));
        purchase("{\"quantity\":null}").andExpect(status().isOk()).andExpect(jsonPath("$.purchased").value(true));
        var captor = org.mockito.ArgumentCaptor.forClass(InventoryItem.class);
        verify(inventory).save(captor.capture());
        assertThat(captor.getValue().quantity()).isNull();
        assertThat(stored.get().inventoryWasPresent()).isFalse();
    }

    @Test void invalidQuantityDoesNotPurchaseOrChangeInventory() throws Exception {
        purchase("{\"quantity\":0}").andExpect(status().isBadRequest());
        verifyNoInteractions(inventory);
        verify(shopping, never()).save(any());
    }

    private org.springframework.test.web.servlet.ResultActions purchase(String body) throws Exception {
        var request = post("/v1/shopping-list/items/" + id + "/purchase").with(user("shopper")).with(csrf());
        if (body != null) request.contentType("application/json").content(body);
        return mvc.perform(request);
    }
}
