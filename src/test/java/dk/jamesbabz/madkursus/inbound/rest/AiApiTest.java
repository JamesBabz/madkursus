package dk.jamesbabz.madkursus.inbound.rest;

import dk.jamesbabz.madkursus.inbound.rest.ai.AiApiDelegateImpl;
import dk.jamesbabz.madkursus.inbound.security.SecurityConfig;
import dk.jamesbabz.madkursus.service.applications.AiChatService;
import dk.jamesbabz.madkursus.service.exceptions.AiUnavailableException;
import dk.jamesbabz.madkursus.service.models.AiChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AiApiController.class)
@Import({SecurityConfig.class, AiApiDelegateImpl.class})
class AiApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean AiChatService service;
    @MockitoBean UserDetailsService userDetailsService;

    @Test
    void returnsConfiguredChatModelWithoutCaching() throws Exception {
        when(service.configuredModel()).thenReturn("gemma3:4b");
        mvc.perform(get("/v1/ai/chat/model").with(user("cook")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.model").value("gemma3:4b"))
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(service).configuredModel();
        verifyNoMoreInteractions(service);
    }

    @Test
    void modelMetadataRequiresAuthentication() throws Exception {
        mvc.perform(get("/v1/ai/chat/model")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void returnsTextAnswer() throws Exception {
        when(service.chat("What can I make?", null)).thenReturn(new AiChatResponse("Try potatoes."));
        mvc.perform(post("/v1/ai/chat").with(user("cook")).with(csrf())
                        .contentType("application/json").content("{\"message\":\"What can I make?\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.answer").value("Try potatoes."));
        verify(service).chat("What can I make?", null);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 21})
    void rejectsInvalidAdditionalIngredientLimit(int limit) throws Exception {
        mvc.perform(post("/v1/ai/chat").with(user("cook")).with(csrf()).contentType("application/json")
                .content("{\"message\":\"Dinner?\",\"maxAdditionalIngredients\":"+limit+"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void passesExplicitLimitToTheService() throws Exception {
        when(service.chat("Dinner?",2)).thenReturn(new AiChatResponse("Et forslag"));
        mvc.perform(post("/v1/ai/chat").with(user("cook")).with(csrf()).contentType("application/json")
                .content("{\"message\":\"Dinner?\",\"maxAdditionalIngredients\":2}"))
                .andExpect(status().isOk());
        verify(service).chat("Dinner?",2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"message\":null}", "{\"message\":\"\"}", "{\"message\":\"   \"}"})
    void rejectsInvalidInput(String body) throws Exception {
        mvc.perform(post("/v1/ai/chat").with(user("cook")).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsOversizedMessage() throws Exception {
        mvc.perform(post("/v1/ai/chat").with(user("cook")).with(csrf())
                        .contentType("application/json").content("{\"message\":\"" + "a".repeat(4001) + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void requiresAuthenticationAndCsrf() throws Exception {
        mvc.perform(post("/v1/ai/chat").with(csrf()).contentType("application/json")
                        .content("{\"message\":\"Dinner?\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/ai/chat").with(user("cook")).contentType("application/json")
                        .content("{\"message\":\"Dinner?\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void providerFailureReturns503InExistingErrorFormat() throws Exception {
        when(service.chat("Dinner?", null)).thenThrow(new AiUnavailableException(new RuntimeException("private details")));
        mvc.perform(post("/v1/ai/chat").with(user("cook")).with(csrf())
                        .contentType("application/json").content("{\"message\":\"Dinner?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("The meal-planning assistant is temporarily unavailable. Please try again later."))
                .andExpect(jsonPath("$.correlationId").exists());
    }
    @Test void knownRecipesExtendTheTextContractWithTypedNavigationData() throws Exception {
        var id=java.util.UUID.randomUUID();
        var match=new dk.jamesbabz.madkursus.service.models.RecipeMatch(id,dk.jamesbabz.madkursus.service.models.RecipeMatch.Source.TEMPLATE,"Known meal",dk.jamesbabz.madkursus.service.models.RecipeMatch.State.COOKABLE,java.util.List.of(),java.util.List.of());
        when(service.chat("Dinner?",1)).thenReturn(new AiChatResponse("Known recipes",java.util.List.of(match)));
        mvc.perform(post("/v1/ai/chat").with(user("cook")).with(csrf()).contentType("application/json").content("{\"message\":\"Dinner?\",\"maxAdditionalIngredients\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.answer").value("Known recipes"))
                .andExpect(jsonPath("$.knownRecipes[0].id").value(id.toString())).andExpect(jsonPath("$.knownRecipes[0].source").value("TEMPLATE"))
                .andExpect(jsonPath("$.knownRecipes[0].state").value("COOKABLE")).andExpect(jsonPath("$.knownRecipes[0].missingIngredientCount").value(0));
    }
}
