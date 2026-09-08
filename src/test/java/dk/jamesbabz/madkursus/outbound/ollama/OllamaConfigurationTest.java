package dk.jamesbabz.madkursus.outbound.ollama;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withInitializer(context -> context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()))
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withUserConfiguration(OllamaChatAdapter.class);

    @Test
    void applicationDefaultsCreateAdapterWithoutContactingOllama() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(OllamaChatAdapter.class);
            assertThat(context.getEnvironment().getProperty("madkursus.ai.ollama.model")).isEqualTo("llama3.1:8b");
            assertThat(context.getEnvironment().getProperty("madkursus.ai.ollama.read-timeout")).isEqualTo("120s");
        });
    }

    @Test
    void environmentPlaceholdersCanOverrideDefaults() {
        contextRunner.withPropertyValues("OLLAMA_MODEL=another-model", "OLLAMA_BASE_URL=http://home-server:11434",
                "OLLAMA_CONNECT_TIMEOUT=2s", "OLLAMA_READ_TIMEOUT=90s").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(OllamaChatAdapter.class);
            assertThat(context.getEnvironment().getProperty("madkursus.ai.ollama.model")).isEqualTo("another-model");
            assertThat(context.getBean(OllamaChatAdapter.class).configuredModel()).isEqualTo("another-model");
            assertThat(context.getEnvironment().getProperty("madkursus.ai.ollama.base-url")).isEqualTo("http://home-server:11434");
            assertThat(context.getEnvironment().getProperty("madkursus.ai.ollama.connect-timeout")).isEqualTo("2s");
            assertThat(context.getEnvironment().getProperty("madkursus.ai.ollama.read-timeout")).isEqualTo("90s");
        });
    }
}
