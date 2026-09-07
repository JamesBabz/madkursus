package dk.jamesbabz.madkursus.outbound.ollama;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;

import dk.jamesbabz.madkursus.service.exceptions.AiUnavailableException;
import dk.jamesbabz.madkursus.service.models.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OllamaChatAdapterTest {
    private MockRestServiceServer server;
    private OllamaChatAdapter adapter;
    private final AiChatRequest request = new AiChatRequest(List.of(
            new AiChatMessage(AiChatMessage.Role.SYSTEM, "Inventory: potatoes"),
            new AiChatMessage(AiChatMessage.Role.USER, "Dinner?")));

    private void mockServer() {
        // The adapter takes a builder so tests can substitute transport without an Ollama process.
        var builder = org.mockito.Mockito.spy(RestClient.builder());
        org.mockito.Mockito.doReturn(builder).when(builder).clone();
        server = MockRestServiceServer.bindTo(builder).build();
        org.mockito.Mockito.doReturn(builder).when(builder).requestFactory(org.mockito.ArgumentMatchers.any());
        adapter = new OllamaChatAdapter(builder, "http://home-server:11434", "configured-model",
                Duration.ofSeconds(2), Duration.ofSeconds(10));
    }

    @Test
    void sendsConfiguredModelAndOrderedMessagesWithoutStreaming() {
        mockServer();
        server.expect(requestTo("http://home-server:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"model":"configured-model","stream":false,"messages":[
                          {"role":"system","content":"Inventory: potatoes"},
                          {"role":"user","content":"Dinner?"}]}
                        """))
                .andRespond(withSuccess("""
                        {"message":{"role":"assistant","content":"Roasted potatoes"},"done":true,"total_duration":42}
                        """, MediaType.APPLICATION_JSON));
        assertThat(adapter.chat(request).answer()).isEqualTo("Roasted potatoes");
        server.verify();
    }

    @Test
    void convertsHttpErrorToProviderIndependentFailure() {
        mockServer();
        server.expect(requestTo("http://home-server:11434/api/chat"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("private provider details"));
        assertThatThrownBy(() -> adapter.chat(request)).isInstanceOf(AiUnavailableException.class)
                .hasMessageNotContaining("private provider details");
        server.verify();
    }

    @Test
    void convertsConnectionOrReadTimeoutToProviderIndependentFailure() {
        mockServer();
        server.expect(requestTo("http://home-server:11434/api/chat"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));
        assertThatThrownBy(() -> adapter.chat(request)).isInstanceOf(AiUnavailableException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not json", "{}", "{\"message\":{\"role\":\"assistant\",\"content\":\" \"},\"done\":true}",
            "{\"message\":{\"role\":\"assistant\",\"content\":\"partial\"},\"done\":false}"})
    void rejectsInvalidOrIncompleteResponses(String body) {
        mockServer();
        server.expect(requestTo("http://home-server:11434/api/chat"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> adapter.chat(request)).isInstanceOf(AiUnavailableException.class);
        server.verify();
    }

    @Test
    void rejectsUnlimitedTimeoutConfiguration() {
        assertThatThrownBy(() -> new OllamaChatAdapter(RestClient.builder(), "http://localhost", "model",
                Duration.ZERO, Duration.ofSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }
}
