package dk.jamesbabz.madkursus.service.applications;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import dk.jamesbabz.madkursus.inbound.security.AuthenticatedUser;
import dk.jamesbabz.madkursus.inbound.security.SecurityCurrentUserProvider;
import dk.jamesbabz.madkursus.service.exceptions.AiUnavailableException;
import dk.jamesbabz.madkursus.service.exceptions.InvalidInputException;
import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.ports.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiChatServiceTest {
    private final InventoryPort inventoryPort = mock(InventoryPort.class);
    private final AiChatPort aiPort = mock(AiChatPort.class);
    private final UUID userId = UUID.randomUUID();
    private final AiChatService service = new AiChatService(new InventoryService(inventoryPort,
            mock(ProductService.class), mock(ProductTemplateService.class), new SecurityCurrentUserProvider(),
            mock(InventoryAvailabilityService.class)), aiPort);

    @BeforeEach
    void authenticate() {
        var user = new AuthenticatedUser(userId, "cook", "unused", true);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
    }

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void loadsAuthenticatedInventoryAndSuppliesMessageAndReturnsAnswer() {
        var potatoes = new Product(UUID.randomUUID(), userId, "Potatoes", ProductCategory.OTHER, Unit.GRAM);
        var salt = new Product(UUID.randomUUID(), userId, null, "Salt", ProductCategory.SPICE,
                Unit.GRAM, InventoryTrackingMode.PRESENCE);
        when(inventoryPort.findAllByUserId(userId)).thenReturn(List.of(
                new InventoryItem(UUID.randomUUID(), potatoes, new BigDecimal("750")),
                new InventoryItem(UUID.randomUUID(), salt, null)));
        when(aiPort.chat(any())).thenReturn(new AiChatResponse("Try roasted potatoes; buy some oil."));

        assertThat(service.chat("Anything with potatoes? I don't want eggs.").answer())
                .isEqualTo("Try roasted potatoes; buy some oil.");

        verify(inventoryPort).findAllByUserId(userId);
        verifyNoMoreInteractions(inventoryPort);
        var request = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiPort).chat(request.capture());
        assertThat(request.getValue().messages()).hasSize(3);
        assertThat(request.getValue().messages().get(1).content())
                .contains("Potatoes: 750 GRAM", "Salt: present; quantity unknown")
                .doesNotContain(userId.toString());
        assertThat(request.getValue().messages().get(2))
                .isEqualTo(new AiChatMessage(AiChatMessage.Role.USER, "Anything with potatoes? I don't want eggs."));
    }

    @Test
    void reloadsInventoryOnEveryRequestAndHandlesEmptyInventory() {
        when(inventoryPort.findAllByUserId(userId)).thenReturn(List.of());
        when(aiPort.chat(any())).thenReturn(new AiChatResponse("What are you willing to buy?"));
        service.chat("Dinner?");
        service.chat("Anything else?");
        verify(inventoryPort, times(2)).findAllByUserId(userId);
        verify(aiPort, times(2)).chat(argThat(request -> request.messages().get(1).content().contains("(empty)")));
    }

    @Test
    void propagatesProviderFailure() {
        when(inventoryPort.findAllByUserId(userId)).thenReturn(List.of());
        when(aiPort.chat(any())).thenThrow(new AiUnavailableException());
        assertThatThrownBy(() -> service.chat("Dinner?")).isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void rejectsInvalidMessagesBeforeLoadingInventory() {
        for (String message : new String[] {null, "", " \n ", "a".repeat(4001)}) {
            assertThatThrownBy(() -> service.chat(message)).isInstanceOf(InvalidInputException.class);
        }
        verifyNoInteractions(inventoryPort, aiPort);
    }
}
