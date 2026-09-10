package dk.jamesbabz.madkursus.service.applications;

import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.ports.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.math.BigDecimal;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class RecipeMatchingServiceTest {
    final UUID user = UUID.randomUUID();
    final CurrentUserProvider current = mock(CurrentUserProvider.class);
    final InventoryPort inventory = mock(InventoryPort.class);
    final MealPlanPort plans = mock(MealPlanPort.class);
    final RecipePort recipes = mock(RecipePort.class);
    final RecipeTemplatePort templates = mock(RecipeTemplatePort.class);
    final RecipeMatchingService service = new RecipeMatchingService(current, new InventoryAvailabilityService(inventory, plans, current, new RecipeQuantityNormalizer()), recipes, templates, new RecipeQuantityNormalizer(), new RecipeMatchRanker());
    @BeforeEach void setup() { when(current.currentUserId()).thenReturn(user); }
    @Test void ownedPlanDiscoveryExcludesIdentitiesAndReusesReservationsAndRanking() {
        var egg = template("Egg", Unit.GRAM);
        var chicken = template("Chicken", Unit.GRAM);
        var other = template("Other", Unit.GRAM);
        when(recipes.findAllByUserId(user)).thenReturn(List.of(
                recipe("A egg", ingredient(egg, "10", RecipeUnit.GRAM)),
                recipe("Z chicken", ingredient(chicken, "100", RecipeUnit.GRAM)),
                recipe("B other", ingredient(other, "10", RecipeUnit.GRAM))));
        when(inventory.findAllByUserId(user)).thenReturn(List.of(stock(chicken, "200")));
        when(plans.findAllByUserId(user)).thenReturn(List.of(plan(chicken, "100", RecipeUnit.GRAM, PlannedRecipeStatus.PLANNED)));
        var result = service.findOwnedMatches(null, Set.of(chicken.id()), Set.of(egg.id()), 2);
        assertThat(result).extracting(RecipeMatch::name).containsExactly("Z chicken", "B other");
        assertThat(result).allMatch(r -> r.source() == RecipeMatch.Source.RECIPE);
        assertThat(result.getFirst().missingIngredients().getFirst().shortage()).isEqualByComparingTo("100");
        verifyNoInteractions(templates);
        assertThat(service.findOwnedMatches(0, Set.of(), Set.of(egg.id()), 2)).isEmpty();
    }
    @Test void templateCatalogIsNeverUsedToFillOwnedCandidates() {
        assertThat(service.findOwnedMatches(null, Set.of(), Set.of(), 2)).isEmpty();
        verifyNoInteractions(templates);
    }
    @Test void twoPortionsScaleRequirementsBeforeShortagesFilteringAndRanking() {
        var pasta=template("Pasta",Unit.GRAM);
        var larger=recipe("A larger",ingredient(pasta,"200",RecipeUnit.GRAM));
        var smaller=recipe("Z smaller",ingredient(pasta,"100",RecipeUnit.GRAM));
        when(recipes.findAllByUserId(user)).thenReturn(List.of(larger,smaller));
        when(inventory.findAllByUserId(user)).thenReturn(List.of(stock(pasta,"300")));
        assertThat(service.findMatches(0)).hasSize(2);
        var two=service.findMatches(1,Set.of(),2);
        assertThat(two).extracting(RecipeMatch::name).containsExactly("Z smaller","A larger");
        assertThat(two.getLast().missingIngredientCount()).isEqualTo(1);
        assertThat(two.getLast().missingIngredients().getFirst().shortage()).isEqualByComparingTo("100");
        assertThat(service.findMatches(0,Set.of(pasta.id()),2)).extracting(RecipeMatch::name).containsExactly("Z smaller");
        when(inventory.findAllByUserId(user)).thenReturn(List.of(stock(pasta,"400")));
        assertThat(service.findMatches(0,Set.of(),2)).hasSize(2).allMatch(m -> m.state()==RecipeMatch.State.COOKABLE);
        assertThat(larger.ingredients().getFirst().quantity()).isEqualByComparingTo("200");
    }
    @Test void twoPortionsPreservePresenceSemantics() {
        var salt=new ProductTemplate(UUID.randomUUID(),"Salt",ProductCategory.SPICE,Unit.GRAM,InventoryTrackingMode.PRESENCE,List.of(),true);
        when(recipes.findAllByUserId(user)).thenReturn(List.of(recipe("Saltret",ingredient(salt,"1",RecipeUnit.TEASPOON))));
        when(inventory.findAllByUserId(user)).thenReturn(List.of(stock(salt,null)));
        assertThat(service.findMatches(null,Set.of(),2)).isEqualTo(service.findMatches(null));
        assertThat(service.findMatches(0,Set.of(),2)).isEmpty();
    }
    @Test void reservationsReduceDiscoveryStockAndAggregateAcrossPlans() {
        var beef=template("Beef",Unit.GRAM);
        when(recipes.findAllByUserId(user)).thenReturn(List.of(recipe("Dinner",ingredient(beef,"400",RecipeUnit.GRAM))));
        when(inventory.findAllByUserId(user)).thenReturn(List.of(stock(beef,"400")));
        when(plans.findAllByUserId(user)).thenReturn(List.of(plan(beef,"400",RecipeUnit.GRAM,PlannedRecipeStatus.PLANNED)));
        var full=service.findMatches(null).getFirst();
        assertThat(full.state()).isEqualTo(RecipeMatch.State.NEAR_MATCH);
        assertThat(full.missingIngredients().getFirst().shortage()).isEqualByComparingTo("400");
        assertThat(service.findMatches(0)).isEmpty();
        when(inventory.findAllByUserId(user)).thenReturn(List.of(stock(beef,"500")));
        when(plans.findAllByUserId(user)).thenReturn(List.of(plan(beef,"300",RecipeUnit.GRAM,PlannedRecipeStatus.PLANNED)));
        assertThat(service.findMatches(null).getFirst().missingIngredients().getFirst().shortage()).isEqualByComparingTo("200");
        when(plans.findAllByUserId(user)).thenReturn(List.of(plan(beef,"200",RecipeUnit.GRAM,PlannedRecipeStatus.PLANNED),plan(beef,"150",RecipeUnit.GRAM,PlannedRecipeStatus.PLANNED)));
        assertThat(service.findMatches(null).getFirst().missingIngredients().getFirst().shortage()).isEqualByComparingTo("250");
    }
    @Test void cookedAndSkippedDoNotReserveDiscoveryStock() {
        var beef=template("Beef",Unit.GRAM);
        when(recipes.findAllByUserId(user)).thenReturn(List.of(recipe("Dinner",ingredient(beef,"400",RecipeUnit.GRAM))));
        when(inventory.findAllByUserId(user)).thenReturn(List.of(stock(beef,"400")));
        when(plans.findAllByUserId(user)).thenReturn(List.of(plan(beef,"400",RecipeUnit.GRAM,PlannedRecipeStatus.COOKED),plan(beef,"400",RecipeUnit.GRAM,PlannedRecipeStatus.SKIPPED)));
        assertThat(service.findMatches(0).getFirst().state()).isEqualTo(RecipeMatch.State.COOKABLE);
        verify(inventory).findAllByUserId(user);
        verify(plans).findAllByUserId(user);
    }
    @Test void failedReservationConversionIsUncertainNotFreeStock() {
        var beef=template("Beef",Unit.GRAM);
        when(recipes.findAllByUserId(user)).thenReturn(List.of(recipe("Dinner",ingredient(beef,"400",RecipeUnit.GRAM))));
        when(inventory.findAllByUserId(user)).thenReturn(List.of(stock(beef,"400")));
        when(plans.findAllByUserId(user)).thenReturn(List.of(plan(beef,"1",RecipeUnit.PIECE,PlannedRecipeStatus.PLANNED)));
        var result=service.findMatches(null).getFirst();
        assertThat(result.state()).isEqualTo(RecipeMatch.State.CHECK_QUANTITIES);
        assertThat(result.uncertainIngredients()).containsExactly("Beef");
        assertThat(result.missingIngredients()).isEmpty(); // Unknown, not a fabricated shortage.
        assertThat(service.findMatches(0)).isEmpty();
    }
    @Test void legacyNamesDoNotEstablishStockIdentityAndCanonicalMetadataMustBeCompatible() {
        var beef=template("Beef",Unit.GRAM);
        when(recipes.findAllByUserId(user)).thenReturn(List.of(recipe("Dinner",ingredient(beef,"400",RecipeUnit.GRAM))));
        var legacy=new Product(UUID.randomUUID(),user,null,"Beef",beef.category(),Unit.GRAM,InventoryTrackingMode.QUANTITY);
        when(inventory.findAllByUserId(user)).thenReturn(List.of(new InventoryItem(UUID.randomUUID(),legacy,new BigDecimal("500"))));
        assertThat(service.findMatches(null).getFirst().missingIngredients().getFirst().shortage()).isEqualByComparingTo("400");
        for(var unit:List.of(Unit.MILLILITER,Unit.GRAM)) {
            var incompatible=new Product(legacy.id(),user,beef.id(),"Renamed",beef.category(),unit,unit==Unit.GRAM?InventoryTrackingMode.UNTRACKED:InventoryTrackingMode.QUANTITY);
            when(inventory.findAllByUserId(user)).thenReturn(List.of(new InventoryItem(UUID.randomUUID(),incompatible,new BigDecimal("500"))));
            assertThat(service.findMatches(null).getFirst().state()).isEqualTo(RecipeMatch.State.CHECK_QUANTITIES);
        }
    }
    MealPlan plan(ProductTemplate template,String quantity,RecipeUnit unit,PlannedRecipeStatus status) {
        return new MealPlan(UUID.randomUUID(),user,"Existing",null,null,List.of(new PlannedRecipe(UUID.randomUUID(),recipe("Reserved",ingredient(template,quantity,unit)),1,1,status)));
    }
    ProductTemplate template(String name, Unit unit) { return new ProductTemplate(UUID.randomUUID(), name, ProductCategory.OTHER, unit, List.of(), true); }
    RecipeIngredient ingredient(ProductTemplate template, String quantity, RecipeUnit unit) { return new RecipeIngredient(UUID.randomUUID(), template, new BigDecimal(quantity), unit, null, 1); }
    Recipe recipe(String name, RecipeIngredient... ingredients) { return new Recipe(UUID.randomUUID(),user,name,null,null,null,List.of(ingredients),List.of()); }
    InventoryItem stock(ProductTemplate template, String quantity) {
        return new InventoryItem(UUID.randomUUID(),new Product(UUID.randomUUID(),user,template.id(),"Renamed " + template.name(),template.category(),template.defaultUnit(),template.defaultTrackingMode()),quantity == null ? null : new BigDecimal(quantity));
    }
    @Test void exactKnownRecipeUsesCanonicalIdentityAndOnlyCurrentUserQueries() {
        var pasta=template("Farfalle",Unit.GRAM);var recipe=recipe("Kødboller i tomatsovs med pasta",ingredient(pasta,"200",RecipeUnit.GRAM));
        when(recipes.findAllByUserId(user)).thenReturn(List.of(recipe));when(inventory.findAllByUserId(user)).thenReturn(List.of(stock(pasta,"1000")));
        var matches=service.findMatches(0);
        assertThat(matches).hasSize(1);assertThat(matches.getFirst().state()).isEqualTo(RecipeMatch.State.COOKABLE);
        assertThat(matches.getFirst().id()).isEqualTo(recipe.id());assertThat(matches.getFirst().missingIngredientCount()).isZero();
        verify(inventory).findAllByUserId(user);verifyNoMoreInteractions(inventory);
    }
    @Test void oneMissingIngredientAndZeroOrOneLimits() {
        var pasta=template("Pasta",Unit.GRAM);var tomato=template("Tomat",Unit.GRAM);
        when(recipes.findAllByUserId(user)).thenReturn(List.of(recipe("Pasta",ingredient(pasta,"200",RecipeUnit.GRAM),ingredient(tomato,"100",RecipeUnit.GRAM))));
        when(inventory.findAllByUserId(user)).thenReturn(List.of(stock(pasta,"200")));
        assertThat(service.findMatches(0)).isEmpty();var match=service.findMatches(1).getFirst();
        assertThat(match.state()).isEqualTo(RecipeMatch.State.NEAR_MATCH);assertThat(match.missingIngredients()).extracting(RecipeMatch.MissingIngredient::name).containsExactly("Tomat");
    }
    @Test void duplicateRequirementsAggregateAndCountShortageOnce() {
        var pasta=template("Pasta",Unit.GRAM);
        when(recipes.findAllByUserId(user)).thenReturn(List.of(recipe("Pasta",ingredient(pasta,"150",RecipeUnit.GRAM),ingredient(pasta,"100",RecipeUnit.GRAM))));
        when(inventory.findAllByUserId(user)).thenReturn(List.of(stock(pasta,"200")));
        var result=service.findMatches(1).getFirst();assertThat(result.missingIngredientCount()).isEqualTo(1);
        assertThat(result.missingIngredients().getFirst().shortage()).isEqualByComparingTo("50");
    }
    @Test void presenceIsUncertainNeverExactEvenWithoutAQuantityConversion() {
        var salt=new ProductTemplate(UUID.randomUUID(),"Salt",ProductCategory.SPICE,Unit.GRAM,InventoryTrackingMode.PRESENCE,List.of(),true);
        when(recipes.findAllByUserId(user)).thenReturn(List.of(recipe("Saltret",ingredient(salt,"1",RecipeUnit.TEASPOON))));
        when(inventory.findAllByUserId(user)).thenReturn(List.of(stock(salt,null)));
        var result=service.findMatches(null).getFirst();assertThat(result.state()).isEqualTo(RecipeMatch.State.CHECK_QUANTITIES);
        assertThat(result.uncertainIngredients()).containsExactly("Salt");assertThat(result.missingIngredientCount()).isZero();
        assertThat(service.findMatches(0)).isEmpty();
        when(inventory.findAllByUserId(user)).thenReturn(List.of());
        assertThat(service.findMatches(1).getFirst().missingIngredientCount()).isEqualTo(1);
    }
    @Test void noNameMatchingOrOtherUsersStock() {
        var requirement=template("Pasta",Unit.GRAM);var impostor=template("Pasta",Unit.GRAM);
        var foreign=stock(requirement,"1000");var p=foreign.product();
        foreign=new InventoryItem(foreign.id(),new Product(p.id(),UUID.randomUUID(),p.sourceTemplateId(),p.name(),p.category(),p.defaultUnit(),p.inventoryTrackingMode()),foreign.quantity());
        when(recipes.findAllByUserId(user)).thenReturn(List.of(recipe("Ret",ingredient(requirement,"200",RecipeUnit.GRAM))));
        when(inventory.findAllByUserId(user)).thenReturn(List.of(stock(impostor,"1000"),foreign));
        assertThat(service.findMatches(0)).isEmpty();assertThat(service.findMatches(null).getFirst().missingIngredientCount()).isEqualTo(1);
    }
    @Test void unitConversionIsReusedAndUnsupportedConversionIsNotCalledCookable() {
        var milk=template("Mælk",Unit.MILLILITER);var flour=template("Mel",Unit.GRAM);
        when(recipes.findAllByUserId(user)).thenReturn(List.of(recipe("Mælk",ingredient(milk,"2",RecipeUnit.DECILITER)),recipe("Mel",ingredient(flour,"1",RecipeUnit.PIECE))));
        when(inventory.findAllByUserId(user)).thenReturn(List.of(stock(milk,"200"),stock(flour,"500")));
        assertThat(service.findMatches(null)).extracting(RecipeMatch::name).containsExactly("Mælk");
    }
    @Test void knownCatalogIsIncludedCopiedRecipesDeduplicatedAndOrderingStable() {
        var food=template("Food",Unit.GRAM);var i=ingredient(food,"1",RecipeUnit.GRAM);
        var t=new RecipeTemplate(UUID.randomUUID(),"Catalog",null,true,null,null,List.of(new RecipeTemplateIngredient(i.id(),food,i.quantity(),i.unit(),null,1)),List.of());
        when(templates.search(null)).thenReturn(List.of(t));when(inventory.findAllByUserId(user)).thenReturn(List.of(stock(food,"10")));
        assertThat(service.findMatches(null).getFirst().source()).isEqualTo(RecipeMatch.Source.TEMPLATE);
        var copy=new Recipe(UUID.randomUUID(),user,t.id(),"Z own",null,null,null,List.of(i),List.of());
        var a=recipe("A own",i);when(recipes.findAllByUserId(user)).thenReturn(List.of(copy,a));
        assertThat(service.findMatches(null)).extracting(RecipeMatch::name).containsExactly("A own","Z own");
        when(recipes.findAllByUserId(user)).thenReturn(List.of(a,copy));
        assertThat(service.findMatches(null)).extracting(RecipeMatch::name).containsExactly("A own","Z own");
    }
    @Test void actualCuratedMeatballsMatchTheReportedInventoryWithoutAi() throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String,ProductTemplate> catalog=new HashMap<>();
        try(var stream=getClass().getResourceAsStream("/seed/product-templates.json")) {
            for(var node:json.readTree(stream).get("products")) {
                List<ProductTemplateUnitConversion> conversions=new ArrayList<>();
                if(node.has("conversions")) for(var conversion:node.get("conversions")) conversions.add(json.treeToValue(conversion,ProductTemplateUnitConversion.class));
                catalog.put(node.get("key").asText(),new ProductTemplate(UUID.fromString(node.get("id").asText()),node.get("name").asText(),ProductCategory.valueOf(node.get("category").asText()),Unit.valueOf(node.get("defaultUnit").asText()),InventoryTrackingMode.valueOf(node.get("defaultTrackingMode").asText()),List.of(),true,conversions));
            }
        }
        List<RecipeIngredient> requirements=new ArrayList<>();String name;
        try(var stream=getClass().getResourceAsStream("/recipe-templates/koedboller-i-tomatsovs-med-pasta.json")) {
            var data=json.readTree(stream);name=data.get("name").asText();
            for(var i:data.get("ingredients")) requirements.add(ingredient(catalog.get(i.get("productTemplate").asText()),i.get("quantity").asText(),RecipeUnit.valueOf(i.get("unit").asText())));
        }
        Map<String,String> quantities=Map.of("FARFALLE","1000","HAKKEDE_TOMATER","400","HAKKET_OKSEKOED","400","HVEDEMEL","18","HVIDLOEG","2","LETMAELK","50","LOEG","1","PENNE","200","AEG","1");
        List<InventoryItem> stock=new ArrayList<>();quantities.forEach((key,amount)->stock.add(stock(catalog.get(key),amount)));
        for(String key:List.of("PAPRIKA","RAPSOLIE","SALT","SORT_PEBER","TOMATPURE","TOERRET_TIMIAN")) {
            var t=catalog.get(key);stock.add(stock(new ProductTemplate(t.id(),t.name(),t.category(),t.defaultUnit(),InventoryTrackingMode.PRESENCE,List.of(),true,t.conversions()),null));
        }
        when(inventory.findAllByUserId(user)).thenReturn(stock);
        when(recipes.findAllByUserId(user)).thenReturn(List.of(new Recipe(UUID.randomUUID(),user,name,null,null,null,requirements,List.of())));
        var match=service.findMatches(null).getFirst();assertThat(match.name()).isEqualTo("Kødboller i tomatsovs med pasta");
        assertThat(match.missingIngredientCount()).isZero();assertThat(match.state()).isEqualTo(RecipeMatch.State.CHECK_QUANTITIES);
        assertThat(match.uncertainIngredients()).contains("Salt","Sort peber");
    }
    @Test void preferredTemplateRanksBeforeNameWithoutChangingMatchEligibility() {
        var chicken=template("Chicken",Unit.GRAM);var beef=template("Beef",Unit.GRAM);
        var a=recipe("A beef",ingredient(beef,"100",RecipeUnit.GRAM));var z=recipe("Z chicken",ingredient(chicken,"100",RecipeUnit.GRAM));
        when(recipes.findAllByUserId(user)).thenReturn(List.of(a,z));when(inventory.findAllByUserId(user)).thenReturn(List.of(stock(beef,"200"),stock(chicken,"200")));
        assertThat(service.findMatches(0,Set.of(chicken.id()))).extracting(RecipeMatch::id).containsExactly(z.id(),a.id());
        assertThat(service.findMatches(0)).extracting(RecipeMatch::id).containsExactly(a.id(),z.id());
    }
    @Test void zeroHardLimitOverridesChickenPreferenceAndNeverReturnsAPurchaseRequirement() {
        var chicken=template("Chicken",Unit.GRAM);var beef=template("Beef",Unit.GRAM);
        var chickenRecipe=recipe("Chicken",ingredient(chicken,"200",RecipeUnit.GRAM));
        var beefRecipe=recipe("Beef",ingredient(beef,"100",RecipeUnit.GRAM));
        when(recipes.findAllByUserId(user)).thenReturn(List.of(chickenRecipe,beefRecipe));
        when(inventory.findAllByUserId(user)).thenReturn(List.of(stock(chicken,"100"),stock(beef,"200")));
        assertThat(service.findMatches(null,Set.of(chicken.id()))).extracting(RecipeMatch::id).containsExactly(chickenRecipe.id(),beefRecipe.id());
        var limited=service.findMatches(0,Set.of(chicken.id()));
        assertThat(limited).extracting(RecipeMatch::id).containsExactly(beefRecipe.id());
        assertThat(limited).allMatch(m -> m.missingIngredientCount()==0 && m.state()==RecipeMatch.State.COOKABLE);
    }
}
