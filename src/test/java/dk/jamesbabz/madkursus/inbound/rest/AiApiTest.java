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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AiApiController.class)
@Import({SecurityConfig.class, AiApiDelegateImpl.class})
class AiApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean AiChatService service;
    @MockitoBean UserDetailsService userDetailsService;

    @Test
    void returnsTextAnswer() throws Exception {
        when(service.chat("What can I make?")).thenReturn(new AiChatResponse("Try potatoes."));
        mvc.perform(post("/v1/ai/chat").with(user("cook")).with(csrf())
                        .contentType("application/json").content("{\"message\":\"What can I make?\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.answer").value("Try potatoes."));
        verify(service).chat("What can I make?");
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
        when(service.chat("Dinner?")).thenThrow(new AiUnavailableException(new RuntimeException("private details")));
        mvc.perform(post("/v1/ai/chat").with(user("cook")).with(csrf())
                        .contentType("application/json").content("{\"message\":\"Dinner?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("The meal-planning assistant is temporarily unavailable. Please try again later."))
                .andExpect(jsonPath("$.correlationId").exists());
    }
}
