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
    private com.fasterxml.jackson.databind.JsonNode sentSchema;
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
        assertThat(adapter.configuredModel()).isEqualTo("configured-model");
        server.expect(requestTo("http://home-server:11434/api/chat"))
                .andExpect(r -> sentSchema = new com.fasterxml.jackson.databind.ObjectMapper().readTree(((org.springframework.mock.http.client.MockClientHttpRequest)r).getBodyAsString()).get("format"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("configured-model"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.format.anyOf[0].properties.suggestions.maxItems").value(3))
                .andExpect(jsonPath("$.format.anyOf[0].properties.suggestions.items.properties.ingredients.items.anyOf[0].properties.quantity.type").value("null"))
                .andExpect(jsonPath("$.format.anyOf[0].properties.suggestions.items.properties.ingredients.items.anyOf[1].required").value(org.hamcrest.Matchers.contains("reference", "quantity", "unit")))
                .andExpect(jsonPath("$.format.anyOf[0].properties.suggestions.items.properties.ingredients.items.anyOf[1].properties.unit.enum").value(org.hamcrest.Matchers.contains("GRAM", "MILLILITER", "PIECE")))
                .andExpect(jsonPath("$.messages[0].content").value("Inventory: potatoes"))
                .andExpect(jsonPath("$.messages[1].content").value("Dinner?"))
                .andRespond(withSuccess("""
                        {"message":{"role":"assistant","content":"{\\"reply\\":\\"NO_SUGGESTIONS\\",\\"suggestions\\":[]}"},"done":true}
                        """, MediaType.APPLICATION_JSON));
        assertThat(adapter.chat(request).reply()).isEqualTo(AiMealProposal.Reply.NO_SUGGESTIONS);
        server.verify();
    }

    @Test
    void convertsHttpErrorToProviderIndependentFailure() {
        mockServer();
        server.expect(requestTo("http://home-server:11434/api/chat"))
                .andExpect(r -> sentSchema = new com.fasterxml.jackson.databind.ObjectMapper().readTree(((org.springframework.mock.http.client.MockClientHttpRequest)r).getBodyAsString()).get("format"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("private provider details"));
        assertThatThrownBy(() -> adapter.chat(request)).isInstanceOf(AiUnavailableException.class)
                .hasMessageNotContaining("private provider details");
        server.verify();
    }

    @Test
    void convertsConnectionOrReadTimeoutToProviderIndependentFailure() {
        mockServer();
        server.expect(requestTo("http://home-server:11434/api/chat"))
                .andExpect(r -> sentSchema = new com.fasterxml.jackson.databind.ObjectMapper().readTree(((org.springframework.mock.http.client.MockClientHttpRequest)r).getBodyAsString()).get("format"))
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
                .andExpect(r -> sentSchema = new com.fasterxml.jackson.databind.ObjectMapper().readTree(((org.springframework.mock.http.client.MockClientHttpRequest)r).getBodyAsString()).get("format"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> adapter.chat(request)).isInstanceOf(AiUnavailableException.class);
        server.verify();
    }

    @Test
    void rejectsUnlimitedTimeoutConfiguration() {
        assertThatThrownBy(() -> new OllamaChatAdapter(RestClient.builder(), "http://localhost", "model",
                Duration.ZERO, Duration.ofSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void optionalQuantitiesParseAndMalformedSiblingIsIsolated() throws Exception {
        mockServer();
        String content = """
                {"reply":"SUGGESTIONS","suggestions":[
                  {"name":"Løgret","ingredients":[{"reference":"product:1"}]},
                  {"name":"Forkert","ingredients":[{"reference":"product:1","unit":"BOGUS"}]},
                  {"name":"Anden ret","ingredients":[{"reference":"product:1","quantity":1,"unit":"PIECE"}]}]}
                """;
        var body=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("done",true,"message",java.util.Map.of("role","assistant","content",content)));
        server.expect(requestTo("http://home-server:11434/api/chat"))
                .andExpect(r -> sentSchema = new com.fasterxml.jackson.databind.ObjectMapper().readTree(((org.springframework.mock.http.client.MockClientHttpRequest)r).getBodyAsString()).get("format")).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        var proposal=adapter.chat(request);
        assertThat(proposal.suggestions()).hasSize(3);
        assertThat(proposal.suggestions().get(0).ingredients().getFirst().quantity()).isNull();
        assertThat(proposal.suggestions().get(1)).isNull();
        assertThat(proposal.suggestions().get(2).ingredients().getFirst().quantity()).isEqualByComparingTo("1");
        server.verify();
    }
    @ParameterizedTest
    @ValueSource(strings = {"null", "{", "{}", "{\"reply\":\"SUGGESTIONS\",\"suggestions\":null}"})
    void malformedStructuredEnvelopeFailsClosed(String content) throws Exception {
        mockServer();
        var body=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("done",true,"message",java.util.Map.of("role","assistant","content",content)));
        server.expect(requestTo("http://home-server:11434/api/chat"))
                .andExpect(r -> sentSchema = new com.fasterxml.jackson.databind.ObjectMapper().readTree(((org.springframework.mock.http.client.MockClientHttpRequest)r).getBodyAsString()).get("format")).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        assertThatThrownBy(()->adapter.chat(request)).isInstanceOf(AiUnavailableException.class);
        server.verify();
    }
    @Test void diagnosticContentIsBoundedEscapedAndMarksTruncation() {
        assertThat(OllamaChatAdapter.diagnosticContent(null)).isEqualTo("null");
        assertThat(OllamaChatAdapter.diagnosticContent("hello\nworld")).isEqualTo("\"hello\\nworld\"").doesNotContain("\n");
        assertThat(OllamaChatAdapter.diagnosticContent("x".repeat(8000))).contains("TRUNCATED").hasSizeLessThan(6100);
        assertThat(OllamaChatAdapter.diagnosticContent("\n".repeat(8000))).contains("TRUNCATED").hasSizeLessThan(6100).doesNotContain("\n");
        assertThat(OllamaChatAdapter.diagnosticContent("short")).doesNotContain("TRUNCATED");
    }
    @ParameterizedTest
    @ValueSource(strings = {"plain prose", "```json\n{}\n```", "{\"wrongField\":true}", "{", "x"})
    void failureContentIsDebugOnlyAndSizeMetricsMatchWireContent(String content) throws Exception {
        mockServer();
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(OllamaChatAdapter.class);
        var previous=logger.getLevel();
        var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender); logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        try {
            var body=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("done",true,"message",java.util.Map.of("role","assistant","content",content)));
            server.expect(requestTo("http://home-server:11434/api/chat"))
                .andExpect(r -> sentSchema = new com.fasterxml.jackson.databind.ObjectMapper().readTree(((org.springframework.mock.http.client.MockClientHttpRequest)r).getBodyAsString()).get("format")).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
            assertThatThrownBy(()->adapter.chat(request)).isInstanceOf(AiUnavailableException.class);
            var raw=appender.list.stream().filter(e->e.getMessage().contains("rawContent=")).toList();
            assertThat(raw).hasSize(1);
            assertThat(raw.getFirst().getLevel()).isEqualTo(ch.qos.logback.classic.Level.DEBUG);
            assertThat(raw.getFirst().getArgumentArray()).containsExactly("configured-model",content.length(),OllamaChatAdapter.diagnosticContent(content));
            var completed=appender.list.stream().filter(e->e.getLevel()==ch.qos.logback.classic.Level.INFO && e.getMessage().contains("responseCharacters=")).findFirst().orElseThrow();
            assertThat(completed.getArgumentArray()[2]).isEqualTo(content.length());
            var generation=appender.list.stream().filter(e->e.getMessage().contains("schemaCharacters=")).findFirst().orElseThrow();
            var schema=sentSchema;
            assertThat(generation.getArgumentArray()[1]).isEqualTo(request.messages().stream().mapToInt(m->m.content().length()).sum());
            assertThat(generation.getArgumentArray()[2]).isEqualTo(schema.toString().length());
            assertThat(appender.list.stream().filter(e->e.getLevel().isGreaterOrEqual(ch.qos.logback.classic.Level.INFO)))
                    .allMatch(e -> !e.getMessage().contains("rawContent=") && !java.util.Arrays.asList(e.getArgumentArray()).contains(content));
            server.verify();
        } finally { logger.detachAppender(appender);logger.setLevel(previous);appender.stop(); }
    }
    @Test void everyAdvertisedReplyParsesAndOnlySuggestionsMayContainMeals() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        java.util.Set<String> advertised = new java.util.HashSet<>();
        for (var reply : AiMealProposal.Reply.values()) {
            mockServer();
            String content=json.writeValueAsString(java.util.Map.of("reply",reply.name(),"suggestions",List.of()));
            String body=json.writeValueAsString(java.util.Map.of("done",true,"message",java.util.Map.of("role","assistant","content",content)));
            server.expect(requestTo("http://home-server:11434/api/chat"))
                    .andExpect(r -> sentSchema=json.readTree(((org.springframework.mock.http.client.MockClientHttpRequest)r).getBodyAsString()).get("format"))
                    .andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
            assertThat(adapter.chat(request).reply()).isEqualTo(reply);
            for (var branch : sentSchema.get("anyOf")) {
                for (var value : branch.at("/properties/reply/enum")) {
                    advertised.add(value.asText());
                    assertThat(json.readValue("\""+value.asText()+"\"",AiMealProposal.Reply.class)).isNotNull();
                    if (!AiMealProposal.Reply.valueOf(value.asText()).allowsSuggestions())
                        assertThat(branch.at("/properties/suggestions/maxItems").asInt()).isZero();
                }
            }
            server.verify();
        }
        assertThat(advertised).containsExactlyInAnyOrderElementsOf(java.util.Arrays.stream(AiMealProposal.Reply.values()).map(Enum::name).toList());
    }

    @Test void actualMealWithOneExtraLimitParsesButNeedDishWithMealsRemainsInvalid() throws Exception {
        String meal = """
                [{"name":"Farfalle med Hakkede Tomater og Oksekød","ingredients":[{"reference":"p0"},{"reference":"p1"},{"reference":"p2"}]}]
                """;
        for (var reply : List.of(AiMealProposal.Reply.SUGGESTIONS,AiMealProposal.Reply.NEED_DISH)) {
            mockServer();
            var inventory=org.mockito.Mockito.mock(dk.jamesbabz.madkursus.service.applications.InventoryService.class);
            var templates=org.mockito.Mockito.mock(dk.jamesbabz.madkursus.service.applications.ProductTemplateService.class);
            org.mockito.Mockito.when(templates.search(null,true)).thenReturn(List.of());
            org.mockito.Mockito.when(inventory.getAll()).thenReturn(java.util.stream.IntStream.range(0,3).mapToObj(i ->
                    new InventoryItem(java.util.UUID.randomUUID(),new Product(java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),"Food"+i,ProductCategory.OTHER,Unit.GRAM),java.math.BigDecimal.TEN)).toList());
            var service=new dk.jamesbabz.madkursus.service.applications.AiChatService(org.mockito.Mockito.mock(dk.jamesbabz.madkursus.service.applications.MealPlanDiscoveryService.class),inventory,adapter,templates,new dk.jamesbabz.madkursus.service.applications.AiSuggestionValidator(),org.mockito.Mockito.mock(dk.jamesbabz.madkursus.service.applications.RecipeMatchingService.class),
                    org.mockito.Mockito.mock(dk.jamesbabz.madkursus.service.ports.AiIntentPort.class),
                    new dk.jamesbabz.madkursus.service.applications.IngredientPreferenceResolver(templates),
                    org.mockito.Mockito.mock(dk.jamesbabz.madkursus.service.ports.CurrentUserProvider.class));
            var content="{\"reply\":\""+reply+"\",\"suggestions\":"+meal+"}";
            var body=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("done",true,"message",java.util.Map.of("role","assistant","content",content)));
            server.expect(requestTo("http://home-server:11434/api/chat"))
                    .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.containsString("Maximum additional ingredients: 1")))
                    .andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
            if (reply==AiMealProposal.Reply.SUGGESTIONS) assertThat(service.chat("Dinner?",1).answer()).contains("Farfalle med Hakkede Tomater og Oksekød");
            else assertThatThrownBy(()->service.chat("Dinner?",1)).isInstanceOf(AiUnavailableException.class);
            server.verify();
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(AiChatIntent.Intent.class)
    void smallIntentRequestUsesSameModelAndOnlyMessageContext(AiChatIntent.Intent kind) throws Exception {
        mockServer();
        String message="Yo. Baseret på det jeg har hjemme, hvad kan jeg så lave?";
        var content=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new AiChatIntent(kind,true,List.of("kylling"),List.of()));
        var body=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("done",true,"message",java.util.Map.of("role","assistant","content",content)));
        server.expect(requestTo("http://home-server:11434/api/chat"))
                .andExpect(jsonPath("$.model").value("configured-model"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.options.temperature").value(0))
                .andExpect(jsonPath("$.messages[0].content").value(OllamaChatAdapter.INTENT_PROMPT))
                .andExpect(jsonPath("$.messages[1].content").value(OllamaChatAdapter.INTENT_USER_PROMPT + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(message)))
                .andExpect(jsonPath("$.format.properties.intent.enum").value(org.hamcrest.Matchers.contains("MEAL_DISCOVERY","MEAL_PLAN_DISCOVERY","GENERAL_COOKING","OTHER")))
                .andExpect(r -> {
                    var wire=new com.fasterxml.jackson.databind.ObjectMapper().readTree(((org.springframework.mock.http.client.MockClientHttpRequest)r).getBodyAsString());
                    assertThat(wire.get("format").toString().length()).isLessThan(1200);
                    assertThat(wire.get("messages").get(0).get("content").asText().length()).isLessThan(2600);
                }).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        var interpreted=adapter.interpret(message);
        assertThat(interpreted.intent()).isEqualTo(kind);assertThat(interpreted.inventoryAware()).isTrue();
        assertThat(interpreted.preferredIngredientTerms()).containsExactly("kylling");server.verify();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"not json","null","{}","{\"intent\":\"UNKNOWN\",\"inventoryAware\":true,\"preferredIngredientTerms\":[],\"excludedIngredientTerms\":[]}","{\"intent\":\"MEAL_DISCOVERY\",\"preferredIngredientTerms\":[],\"excludedIngredientTerms\":[]}","{\"intent\":\"MEAL_DISCOVERY\",\"inventoryAware\":true,\"preferredIngredientTerms\":[\"\"],\"excludedIngredientTerms\":[]}"})
    void invalidIntentOutputIsProviderIndependentFailure(String content) throws Exception {
        mockServer();
        var body=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("done",true,"message",java.util.Map.of("role","assistant","content",content)));
        server.expect(requestTo("http://home-server:11434/api/chat")).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        assertThatThrownBy(()->adapter.interpret("Dinner?")).isInstanceOf(AiUnavailableException.class);server.verify();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"Lav en madplan for 5 dage,5", "Jeg skal bruge aftensmad mandag til fredag,5", "Lav en madplan for 7 dage gerne kylling,7", "Lav 5 retter uden æg,5", "Lav en madplan til 5 dage men ikke pasta hver dag,5"})
    void parsesPlanIntentWithoutDates(String message, int count) throws Exception {
        mockServer();
        var intent = new AiChatIntent(AiChatIntent.Intent.MEAL_PLAN_DISCOVERY, false,
                message.contains("kylling") ? List.of("kylling") : List.of(),
                message.contains("æg") ? List.of("æg") : List.of(), count,
                message.contains("pasta") ? List.of("pasta") : List.of());
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var body = json.writeValueAsString(java.util.Map.of("done", true, "message", java.util.Map.of("role", "assistant", "content", json.writeValueAsString(intent))));
        server.expect(requestTo("http://home-server:11434/api/chat"))
            .andExpect(jsonPath("$.format.properties.requestedMealCount.type").value(org.hamcrest.Matchers.contains("integer", "null")))
            .andExpect(jsonPath("$.messages[1].content").value(OllamaChatAdapter.INTENT_USER_PROMPT + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(message)))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var result = adapter.interpret(message);
        assertThat(result).isEqualTo(intent);
        assertThat(json.writeValueAsString(result)).doesNotContain("date", "2026");
        if (message.contains("pasta")) assertThat(result.excludedIngredientTerms()).isEmpty();
        server.verify();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "uden pasta,pasta,false", "ikke pasta,pasta,false",
        "ikke pasta hver dag,pasta,true", "ikke for meget pasta,pasta,true",
        "gerne mindre pasta,pasta,true", "5 retter uden æg,æg,false"
    })
    void preservesConstraintPolarityInStructuredOutput(String phrase, String term, boolean limited) throws Exception {
        mockServer();
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var intent = new AiChatIntent(AiChatIntent.Intent.MEAL_PLAN_DISCOVERY, false, List.of(),
                limited ? List.of() : List.of(term), 5, limited ? List.of(term) : List.of());
        var body = json.writeValueAsString(java.util.Map.of("done", true, "message", java.util.Map.of(
                "role", "assistant", "content", json.writeValueAsString(intent))));
        server.expect(requestTo("http://home-server:11434/api/chat"))
                .andExpect(jsonPath("$.options.temperature").value(0))
                .andExpect(jsonPath("$.messages[1].content").value(OllamaChatAdapter.INTENT_USER_PROMPT + json.writeValueAsString(phrase)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var result = adapter.interpret(phrase);
        assertThat(result).isEqualTo(intent);
        assertThat(result.preferredIngredientTerms()).isEmpty();
        assertThat(OllamaChatAdapter.INTENT_PROMPT).contains("mutually exclusive", "A negated ingredient is NEVER preferred",
                "ikke pasta hver dag", "gerne mindre pasta", "ikke for meget pasta", "limitedIngredientTerms");
        server.verify();
    }
    @Test void contradictoryRawPlanOutputKeepsLimitedTermOnly() throws Exception {
        mockServer();
        // Raw JSON intentionally bypasses the record constructor to reproduce live provider overlap.
        String content = """
                {"intent":"MEAL_PLAN_DISCOVERY","inventoryAware":false,"requestedMealCount":5,
                 "preferredIngredientTerms":[],"excludedIngredientTerms":[" PASTA "],"limitedIngredientTerms":["pasta"]}
                """;
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var body = json.writeValueAsString(java.util.Map.of("done", true, "message", java.util.Map.of("role", "assistant", "content", content)));
        server.expect(requestTo("http://home-server:11434/api/chat")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var result = adapter.interpret("Lav en madplan til 5 dage, men ikke pasta hver dag");
        assertThat(result.excludedIngredientTerms()).isEmpty();
        assertThat(result.limitedIngredientTerms()).containsExactly("pasta");
        server.verify();
    }
    @Test void intentTimeoutDoesNotChangeProviderFailureContract() {
        mockServer();server.expect(requestTo("http://home-server:11434/api/chat")).andRespond(withException(new SocketTimeoutException("Timed out")));
        assertThatThrownBy(()->adapter.interpret("Dinner?")).isInstanceOf(AiUnavailableException.class);server.verify();
    }
}
