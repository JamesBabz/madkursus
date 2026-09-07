package dk.jamesbabz.madkursus.outbound.ollama;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dk.jamesbabz.madkursus.service.exceptions.AiUnavailableException;
import dk.jamesbabz.madkursus.service.models.AiChatRequest;
import dk.jamesbabz.madkursus.service.models.AiChatResponse;
import dk.jamesbabz.madkursus.service.ports.AiChatPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class OllamaChatAdapter implements AiChatPort {
    private final RestClient client;
    private final String model;

    public OllamaChatAdapter(RestClient.Builder builder,
            @Value("${madkursus.ai.ollama.base-url}") String baseUrl,
            @Value("${madkursus.ai.ollama.model}") String model,
            @Value("${madkursus.ai.ollama.connect-timeout}") Duration connectTimeout,
            @Value("${madkursus.ai.ollama.read-timeout}") Duration readTimeout) {
        if (model.isBlank()) throw new IllegalArgumentException("Ollama model must not be blank");
        if (connectTimeout.toMillis() <= 0 || readTimeout.toMillis() <= 0) {
            throw new IllegalArgumentException("Ollama timeouts must be positive");
        }
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
        this.model = model;
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        var messages = request.messages().stream()
                .map(message -> new Message(message.role().name().toLowerCase(Locale.ROOT), message.content()))
                .toList();
        try {
            var response = client.post().uri("/api/chat").contentType(MediaType.APPLICATION_JSON)
                    .body(new ChatRequest(model, messages, false)).retrieve().body(ChatResponse.class);
            if (response == null || response.message() == null || !response.done()
                    || !"assistant".equals(response.message().role())
                    || response.message().content() == null || response.message().content().isBlank()) {
                throw new AiUnavailableException();
            }
            return new AiChatResponse(response.message().content());
        } catch (RestClientException exception) {
            throw new AiUnavailableException(exception);
        }
    }

    private record ChatRequest(String model, List<Message> messages, boolean stream) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Message(String role, String content) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatResponse(Message message, boolean done) {}
}
