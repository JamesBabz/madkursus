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
            var matcher=new RecipeMatchingService(current,new InventoryAvailabilityService(inventory,mock(MealPlanPort.class),current,new RecipeQuantityNormalizer()),recipes,templates,new RecipeQuantityNormalizer(),new RecipeMatchRanker());
            var intents=mock(AiIntentPort.class);var ai=mock(AiChatPort.class);
            String message="hvad kan jeg lave i aften? Gerne noget med kylling";
            when(intents.interpret(message)).thenReturn(new AiChatIntent(AiChatIntent.Intent.MEAL_DISCOVERY,false,List.of("kylling"),List.of()));
            var chat=new AiChatService(new MealPlanDiscoveryService(matcher, new IngredientPreferenceResolver(catalog)), mock(InventoryService.class),ai,catalog,new AiSuggestionValidator(),matcher,intents,new IngredientPreferenceResolver(catalog),current);
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
            assertThat(catalog.resolveDiscoveryTerm("pasta")).isEmpty();
            var egg = catalog.resolveDiscoveryTerm("æg");
            assertThat(egg).isNotEmpty();
            var ownedChicken = new Recipe(UUID.randomUUID(), user, chicken.name(), null, null, null,
                    chicken.ingredients().stream().map(i -> new RecipeIngredient(i.id(), i.productTemplate(), i.quantity(), i.unit(), i.preparation(), i.sortOrder())).toList(), List.of());
            var ownedEgg = new Recipe(UUID.randomUUID(), user, "Egg dish", null, null, null,
                    List.of(new RecipeIngredient(UUID.randomUUID(), egg.getFirst(), BigDecimal.ONE, RecipeUnit.PIECE, null, 1)), List.of());
            when(recipes.findAllByUserId(user)).thenReturn(List.of(beefRecipe, ownedChicken, ownedEgg));
            when(intents.interpret(message)).thenReturn(new AiChatIntent(AiChatIntent.Intent.MEAL_PLAN_DISCOVERY, true, List.of("kylling"), List.of("æg"), 5, List.of("pasta")));
            var proposal = chat.chat(message).mealPlanProposal();
            assertThat(proposal.candidates()).extracting(RecipeMatch::name).containsExactly(chicken.name(), "Frikadeller");
            assertThat(proposal.candidates()).allMatch(r -> r.source() == RecipeMatch.Source.RECIPE);
            assertThat(proposal.unresolvedLimitedIngredientTerms()).containsExactly("pasta");
            // Persist real owned fixtures in this isolated database, then read them through the real adapter.
            var jdbc = context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
            jdbc.update("INSERT INTO users(id,username,password_hash,created_at,enabled) VALUES (?,?,?,CURRENT_TIMESTAMP,true)", user, "exclusion-test", "unused");
            var omelette = context.getBean(RecipeTemplatePort.class).search(null).stream()
                    .filter(r -> r.name().equals("Æggekage")).findFirst().orElseThrow();
            var eggId = UUID.fromString("60e27233-16b9-395f-8aac-ad23cc1209a4");
            assertThat(egg).extracting(ProductTemplate::id).containsExactly(eggId);
            assertThat(omelette.ingredients()).anyMatch(i -> i.productTemplate().id().equals(eggId));
            var realRecipes = context.getBean(RecipePort.class);
            var now = java.time.Instant.now();
            var savedEgg = realRecipes.save(new Recipe(null, user, omelette.name(), null, now, now,
                    omelette.ingredients().stream().map(i -> new RecipeIngredient(UUID.randomUUID(), i.productTemplate(), i.quantity(), i.unit(), i.preparation(), i.sortOrder())).toList(), List.of()));
            // A misleading name must not exclude an ingredient-safe recipe.
            var savedSafe = realRecipes.save(new Recipe(null, user, "Æggekage uden æg", null, now, now,
                    beefRecipe.ingredients(), List.of()));
            var realMatcher = spy(new RecipeMatchingService(current,
                    new InventoryAvailabilityService(inventory, mock(MealPlanPort.class), current, new RecipeQuantityNormalizer()),
                    realRecipes, templates, new RecipeQuantityNormalizer(), new RecipeMatchRanker()));
            assertThat(realMatcher.findOwnedMatches(null, Set.of(), Set.of(), 2)).extracting(RecipeMatch::id).contains(savedEgg.id(), savedSafe.id());
            var realChat = new AiChatService(new MealPlanDiscoveryService(realMatcher, new IngredientPreferenceResolver(catalog)),
                    mock(InventoryService.class), ai, catalog, new AiSuggestionValidator(), realMatcher, intents, new IngredientPreferenceResolver(catalog), current);
            String eggRequest = "Lav 5 retter uden æg";
            when(intents.interpret(eggRequest)).thenReturn(new AiChatIntent(AiChatIntent.Intent.MEAL_PLAN_DISCOVERY, false, List.of(), List.of("æg"), 5, List.of()));
            var eggResult = realChat.chat(eggRequest).mealPlanProposal();
            assertThat(eggResult.candidates()).extracting(RecipeMatch::id).containsExactly(savedSafe.id());
            assertThat(eggResult.unresolvedExcludedIngredientTerms()).isEmpty();
            verify(realMatcher).findOwnedMatches(null, Set.of(), Set.of(eggId), 2);
            verifyNoInteractions(ai);
        }
    }
}
