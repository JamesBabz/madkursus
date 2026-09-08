package dk.jamesbabz.madkursus.outbound.producttemplate;

import dk.jamesbabz.madkursus.MadkursusApplication;
import dk.jamesbabz.madkursus.service.applications.*;
import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.ports.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import java.util.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiscoveryPreferenceIntegrationTest {
    @Test void migratedCatalogPreferenceReachesRealRecipeRankingAndTwoPortionFiltering() throws Exception {
        try(var postgres=EmbeddedPostgres.start();var context=new SpringApplicationBuilder(MadkursusApplication.class)
                .web(WebApplicationType.SERVLET).run("--server.port=0","--spring.datasource.url="+postgres.getJdbcUrl("postgres","postgres"),
                        "--spring.datasource.username=postgres","--spring.datasource.password=","--spring.jpa.hibernate.ddl-auto=validate")) {
            var catalog=context.getBean(ProductTemplateService.class);
            assertThat(catalog.findByNameOrAlias("kylling")).isEmpty();
            var resolved=catalog.resolveDiscoveryTerm(" Kylling ");
            assertThat(resolved).extracting(ProductTemplate::name).containsExactlyInAnyOrder("Hakket kylling","Hel kylling","Kyllingebryst","Kyllingelår","Kyllingeoverlår","Kyllingeunderlår","Kyllingevinger");
            assertThat(catalog.resolveDiscoveryTerm("kyllingefilet")).extracting(ProductTemplate::name).containsExactly("Kyllingebryst");
            assertThat(catalog.resolveDiscoveryTerm("kyll")).isEmpty();
            var chicken=context.getBean(RecipeTemplatePort.class).search(null).stream().filter(r -> r.name().equals("Pasta med kylling og tomat")).findFirst().orElseThrow();
            assertThat(chicken.ingredients()).anyMatch(i -> i.productTemplate().id().equals(UUID.fromString("47814405-2163-3beb-b98e-5c31ad175fa8")));
            var user=UUID.randomUUID();var current=mock(CurrentUserProvider.class);when(current.currentUserId()).thenReturn(user);
            var inventory=mock(InventoryPort.class);var recipes=mock(RecipePort.class);var templates=mock(RecipeTemplatePort.class);
            var beef=catalog.findByNameOrAlias("hakket oksekød").getFirst();
            var beefRecipe=new Recipe(UUID.randomUUID(),user,"Frikadeller",null,null,null,List.of(new RecipeIngredient(UUID.randomUUID(),beef,new BigDecimal("100"),RecipeUnit.GRAM,null,1)),List.of());
            when(recipes.findAllByUserId(user)).thenReturn(List.of(beefRecipe));when(templates.search(null)).thenReturn(List.of(chicken));
            when(inventory.findAllByUserId(user)).thenReturn(List.of(new InventoryItem(UUID.randomUUID(),new Product(UUID.randomUUID(),user,beef.id(),beef.name(),beef.category(),beef.defaultUnit(),InventoryTrackingMode.QUANTITY),new BigDecimal("200"))));
            var matcher=new RecipeMatchingService(current,inventory,recipes,templates,new RecipeQuantityNormalizer(),new RecipeMatchRanker());
            var intents=mock(AiIntentPort.class);var ai=mock(AiChatPort.class);
            String message="hvad kan jeg lave i aften? Gerne noget med kylling";
            when(intents.interpret(message)).thenReturn(new AiChatIntent(AiChatIntent.Intent.MEAL_DISCOVERY,false,List.of("kylling"),List.of()));
            var chat=new AiChatService(mock(InventoryService.class),ai,catalog,new AiSuggestionValidator(),matcher,intents,new IngredientPreferenceResolver(catalog),current);
            var response=chat.chat(message);
            assertThat(response.answer()).contains("2 portioner");
            assertThat(response.knownRecipes()).extracting(RecipeMatch::name).containsExactly(chicken.name(),"Frikadeller");
            assertThat(response.knownRecipes().getFirst().missingIngredients()).anySatisfy(i -> {
                assertThat(i.productTemplateId()).isEqualTo(UUID.fromString("47814405-2163-3beb-b98e-5c31ad175fa8"));
                assertThat(i.shortage()).isEqualByComparingTo("300");
            });
            assertThat(chat.chat(message,0).knownRecipes()).extracting(RecipeMatch::name).containsExactly("Frikadeller");
            assertThat(chat.chat("Hvad kan jeg lave?").knownRecipes()).extracting(RecipeMatch::name).containsExactly("Frikadeller",chicken.name());
            assertThat(chat.chat(message).knownRecipes()).isEqualTo(response.knownRecipes());
            when(intents.interpret(message)).thenReturn(new AiChatIntent(AiChatIntent.Intent.MEAL_DISCOVERY,false,List.of("unknown"),List.of()));
            assertThat(chat.chat(message).knownRecipes()).extracting(RecipeMatch::name).containsExactly("Frikadeller",chicken.name());
            verifyNoInteractions(ai);
        }
    }
}
