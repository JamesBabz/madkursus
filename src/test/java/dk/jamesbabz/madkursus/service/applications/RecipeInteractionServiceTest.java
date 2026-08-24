package dk.jamesbabz.madkursus.service.applications;

import java.math.BigDecimal; import java.time.Instant; import java.util.*;
import dk.jamesbabz.madkursus.service.models.*; import dk.jamesbabz.madkursus.service.ports.*;
import org.junit.jupiter.api.*; import org.junit.jupiter.api.extension.ExtendWith; import org.mockito.*; import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*; import static org.mockito.ArgumentMatchers.*; import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeInteractionServiceTest {
 @Mock RecipeService recipes; @Mock ProductService products; @Mock InventoryService inventory; @Mock ShoppingListService shopping; @Mock RecipeCookHistoryPort history; @Mock CurrentUserProvider currentUser; @Mock InventoryPort inventoryPort; @Mock MealPlanPort mealPlanPort;
 RecipeInteractionService service;
 UUID user=UUID.randomUUID();
 ProductTemplate rice=new ProductTemplate(UUID.randomUUID(),"Ris",ProductCategory.GRAIN_PASTA,Unit.GRAM,List.of(),false);
 ProductTemplate onion=new ProductTemplate(UUID.randomUUID(),"Løg",ProductCategory.VEGETABLE,Unit.PIECE,List.of(),false);

 @BeforeEach void inventoryDefault(){lenient().when(currentUser.currentUserId()).thenReturn(user);lenient().when(inventoryPort.findAllByUserId(user)).thenReturn(List.of());lenient().when(mealPlanPort.findAllByUserId(user)).thenReturn(List.of());var normalizer=new RecipeQuantityNormalizer();var availability=new InventoryAvailabilityService(inventoryPort,mealPlanPort,currentUser,normalizer);service=new RecipeInteractionService(recipes,products,inventory,shopping,history,currentUser,availability,normalizer);}

 @Test void aggregatesAllRecipesBeforeSubtractingInventoryAndKeepsIndependentPortions(){
  Recipe a=recipe("A",ingredient(rice,"100",RecipeUnit.GRAM),ingredient(onion,"0.5",RecipeUnit.PIECE));Recipe b=recipe("B",ingredient(rice,"50",RecipeUnit.GRAM));
  Product riceProduct=product(rice,InventoryTrackingMode.QUANTITY);when(recipes.get(a.id())).thenReturn(a);when(recipes.get(b.id())).thenReturn(b);when(products.findEquivalent(rice.id(),rice.name())).thenReturn(Optional.of(riceProduct));when(products.findEquivalent(onion.id(),onion.name())).thenReturn(Optional.empty());when(inventoryPort.findAllByUserId(user)).thenReturn(List.of(new InventoryItem(UUID.randomUUID(),riceProduct,new BigDecimal("250"))));
  var result=service.calculate(List.of(new RecipeSelection(a.id(),new BigDecimal("2")),new RecipeSelection(b.id(),new BigDecimal("4"))));
  var r=result.requirements().stream().filter(x->x.productTemplate().id().equals(rice.id())).findFirst().orElseThrow();
  assertThat(r.requiredQuantity()).isEqualByComparingTo("400");assertThat(r.availableQuantity()).isEqualByComparingTo("250");assertThat(r.missingQuantity()).isEqualByComparingTo("150");
  assertThat(result.requirements().stream().filter(x->x.productTemplate().id().equals(onion.id())).findFirst().orElseThrow().requiredQuantity()).isEqualByComparingTo("1");
 }

 @Test void normalizesKnownVolumesAndReportsVolumeToWeightAsUnresolved(){
  ProductTemplate milk=new ProductTemplate(UUID.randomUUID(),"Mælk",ProductCategory.DAIRY,Unit.MILLILITER,List.of(),false);ProductTemplate flour=new ProductTemplate(UUID.randomUUID(),"Mel",ProductCategory.BAKING,Unit.GRAM,List.of(),false);
  Recipe recipe=recipe("Test",ingredient(milk,"1.5",RecipeUnit.DECILITER),ingredient(milk,"2",RecipeUnit.TABLESPOON),ingredient(milk,"1",RecipeUnit.TEASPOON),ingredient(flour,"1",RecipeUnit.TABLESPOON));when(recipes.get(recipe.id())).thenReturn(recipe);when(products.findEquivalent(any(),anyString())).thenReturn(Optional.empty());
  var result=service.calculate(List.of(new RecipeSelection(recipe.id(),BigDecimal.ONE)));var m=result.requirements().stream().filter(r->r.productTemplate().id().equals(milk.id())).findFirst().orElseThrow();
  assertThat(m.requiredQuantity()).isEqualByComparingTo("185");assertThat(result.requirements().stream().filter(r->r.productTemplate().id().equals(flour.id())).findFirst().orElseThrow().warning()).contains("Kan ikke beregnes");
 }

 @Test void presenceUsesAvailabilityWithoutQuantityAndAddsMissingRestockWithoutFakeAmount(){
  ProductTemplate salt=new ProductTemplate(UUID.randomUUID(),"Salt",ProductCategory.SPICE,Unit.GRAM,InventoryTrackingMode.PRESENCE,List.of(),false);Recipe recipe=recipe("Test",ingredient(salt,"5",RecipeUnit.GRAM));when(recipes.get(recipe.id())).thenReturn(recipe);when(products.findEquivalent(salt.id(),salt.name())).thenReturn(Optional.empty());
  var result=service.addMissingToShoppingList(List.of(new RecipeSelection(recipe.id(),BigDecimal.ONE)));
  assertThat(result.requirements().getFirst().missingQuantity()).isNull();verify(shopping).ensureAtLeastFromTemplate(salt.id(),null);
 }

 @Test void untrackedIngredientIsRecipeDataButNeverBecomesARequirementShoppingItemOrDeduction(){
  ProductTemplate water=new ProductTemplate(UUID.randomUUID(),"Vand",ProductCategory.OTHER,Unit.MILLILITER,InventoryTrackingMode.UNTRACKED,List.of(),true);
  ProductTemplate salt=new ProductTemplate(UUID.randomUUID(),"Salt",ProductCategory.SPICE,Unit.GRAM,InventoryTrackingMode.PRESENCE,List.of(),false);
  Recipe recipe=recipe("Sovs",ingredient(rice,"400",RecipeUnit.GRAM),ingredient(salt,"1",RecipeUnit.TEASPOON),ingredient(water,"1",RecipeUnit.DECILITER));Product beef=product(rice,InventoryTrackingMode.QUANTITY);
  when(recipes.get(recipe.id())).thenReturn(recipe);when(products.findEquivalent(rice.id(),rice.name())).thenReturn(Optional.of(beef));when(products.findEquivalent(salt.id(),salt.name())).thenReturn(Optional.empty());when(history.save(any())).thenAnswer(c->c.getArgument(0));when(inventory.consumeUpToAvailable(eq(beef.id()),any())).thenReturn(new InventoryService.Consumption(new BigDecimal("400"),BigDecimal.ZERO));
  var calculated=service.addMissingToShoppingList(List.of(new RecipeSelection(recipe.id(),BigDecimal.ONE)));
  assertThat(calculated.requirements()).extracting(r->r.productTemplate().name()).containsExactly("Ris","Salt").doesNotContain("Vand");verify(shopping,never()).ensureAtLeastFromTemplate(eq(water.id()),any());
  service.cook(recipe.id(),BigDecimal.ONE);verify(inventory,never()).consumeUpToAvailable(argThat(id->!id.equals(beef.id())),any());
 }

 @Test void grinderTurnsUsePresenceWithoutConversionOrNumericDeduction(){
  ProductTemplate pepper=new ProductTemplate(UUID.randomUUID(),"Sort peber",ProductCategory.SPICE,Unit.GRAM,InventoryTrackingMode.PRESENCE,List.of(),false);Recipe recipe=recipe("Peber",ingredient(pepper,"10",RecipeUnit.GRINDER_TURN));Product pp=product(pepper,InventoryTrackingMode.PRESENCE);
  when(recipes.get(recipe.id())).thenReturn(recipe);when(products.findEquivalent(pepper.id(),pepper.name())).thenReturn(Optional.of(pp));
  var absent=service.addMissingToShoppingList(List.of(new RecipeSelection(recipe.id(),BigDecimal.ONE)));assertThat(absent.requirements().getFirst().warning()).isNull();assertThat(absent.requirements().getFirst().satisfied()).isFalse();verify(shopping).ensureAtLeastFromTemplate(pepper.id(),null);
  when(inventoryPort.findAllByUserId(user)).thenReturn(List.of(new InventoryItem(UUID.randomUUID(),pp,null)));when(history.save(any())).thenAnswer(c->c.getArgument(0));
  var cooked=service.cook(recipe.id(),BigDecimal.ONE);assertThat(cooked.deductions().getFirst().satisfied()).isTrue();verify(inventory,never()).consumeUpToAvailable(eq(pp.id()),any());
 }

 @Test void materializingRecipeMissingPiecesRoundsUpToWholeShoppingItemsWithoutChangingCalculation(){
  Recipe recipe=recipe("Æg",ingredient(onion,"0.5",RecipeUnit.PIECE));when(recipes.get(recipe.id())).thenReturn(recipe);when(products.findEquivalent(onion.id(),onion.name())).thenReturn(Optional.empty());
  var calculation=service.addMissingToShoppingList(List.of(new RecipeSelection(recipe.id(),BigDecimal.ONE)));
  assertThat(calculation.requirements().getFirst().requiredQuantity()).isEqualByComparingTo("0.5");
  assertThat(calculation.requirements().getFirst().missingQuantity()).isEqualByComparingTo("0.5");
  verify(shopping).ensureAtLeastFromTemplate(eq(onion.id()),argThat(value->value.compareTo(BigDecimal.ONE)==0));
 }

 @Test void roundsMissingUpToStorageIncrement(){assertThat(service.roundUp(new BigDecimal("100.2"),Unit.GRAM)).isEqualByComparingTo("101");assertThat(service.roundUp(new BigDecimal("0.2"),Unit.PIECE)).isEqualByComparingTo("0.5");}

 @Test void temporaryPlannerUsesAllPersistedReservations(){
  Recipe planned=recipe("Planlagt",ingredient(onion,"8",RecipeUnit.PIECE)),temporary=recipe("Midlertidig",ingredient(onion,"6",RecipeUnit.PIECE));Product product=product(onion,InventoryTrackingMode.QUANTITY);
  MealPlan plan=new MealPlan(UUID.randomUUID(),user,"Plan",Instant.now(),Instant.now(),List.of(new PlannedRecipe(UUID.randomUUID(),planned,1,1,PlannedRecipeStatus.PLANNED)));
  when(mealPlanPort.findAllByUserId(user)).thenReturn(List.of(plan));when(inventoryPort.findAllByUserId(user)).thenReturn(List.of(new InventoryItem(UUID.randomUUID(),product,new BigDecimal("10"))));when(recipes.get(temporary.id())).thenReturn(temporary);when(products.findEquivalent(onion.id(),onion.name())).thenReturn(Optional.of(product));
  RecipeRequirement result=service.calculate(List.of(new RecipeSelection(temporary.id(),BigDecimal.ONE))).requirements().getFirst();
  assertThat(result.physicalQuantity()).isEqualByComparingTo("10");assertThat(result.reservedQuantity()).isEqualByComparingTo("8");assertThat(result.availableQuantity()).isEqualByComparingTo("2");assertThat(result.missingQuantity()).isEqualByComparingTo("4");
  service.addMissingToShoppingList(List.of(new RecipeSelection(temporary.id(),BigDecimal.ONE)));verify(shopping).ensureAtLeastFromTemplate(eq(onion.id()),argThat(value->value.compareTo(new BigDecimal("4"))==0));
 }

 @Test void persistedPlanExcludesItsOwnReservationButCompetesWithOtherPlans(){
  Recipe recipeA=recipe("A",ingredient(onion,"8",RecipeUnit.PIECE)),recipeB=recipe("B",ingredient(onion,"6",RecipeUnit.PIECE));Product product=product(onion,InventoryTrackingMode.QUANTITY);
  MealPlan a=new MealPlan(UUID.randomUUID(),user,"A",Instant.now(),Instant.now(),List.of(new PlannedRecipe(UUID.randomUUID(),recipeA,1,1,PlannedRecipeStatus.PLANNED)));MealPlan b=new MealPlan(UUID.randomUUID(),user,"B",Instant.now(),Instant.now(),List.of(new PlannedRecipe(UUID.randomUUID(),recipeB,1,1,PlannedRecipeStatus.PLANNED)));
  when(mealPlanPort.findAllByUserId(user)).thenReturn(List.of(a,b));when(inventoryPort.findAllByUserId(user)).thenReturn(List.of(new InventoryItem(UUID.randomUUID(),product,new BigDecimal("10"))));when(recipes.get(recipeA.id())).thenReturn(recipeA);when(products.findEquivalent(onion.id(),onion.name())).thenReturn(Optional.of(product));
  RecipeRequirement result=service.calculate(List.of(new RecipeSelection(recipeA.id(),BigDecimal.ONE)),a.id()).requirements().getFirst();
  assertThat(result.reservedQuantity()).isEqualByComparingTo("6");assertThat(result.availableQuantity()).isEqualByComparingTo("4");assertThat(result.missingQuantity()).isEqualByComparingTo("4");
 }

 @Test void cookingDeductsQuantityLeavesPresenceAndRecordsHistoryEvenWithShortage(){
  ProductTemplate salt=new ProductTemplate(UUID.randomUUID(),"Salt",ProductCategory.SPICE,Unit.GRAM,InventoryTrackingMode.PRESENCE,List.of(),false);Recipe recipe=recipe("Mad",ingredient(rice,"200",RecipeUnit.GRAM),ingredient(salt,"2",RecipeUnit.GRAM));Product rp=product(rice,InventoryTrackingMode.QUANTITY);Product sp=product(salt,InventoryTrackingMode.PRESENCE);
  when(recipes.get(recipe.id())).thenReturn(recipe);when(products.findEquivalent(rice.id(),rice.name())).thenReturn(Optional.of(rp));when(products.findEquivalent(salt.id(),salt.name())).thenReturn(Optional.of(sp));when(inventoryPort.findAllByUserId(user)).thenReturn(List.of(new InventoryItem(UUID.randomUUID(),rp,new BigDecimal("300")),new InventoryItem(UUID.randomUUID(),sp,null)));when(inventory.consumeUpToAvailable(eq(rp.id()),argThat(v->v.compareTo(new BigDecimal("400"))==0))).thenReturn(new InventoryService.Consumption(new BigDecimal("300"),new BigDecimal("100")));when(history.save(any())).thenAnswer(c->{var h=(RecipeCookHistory)c.getArgument(0);return new RecipeCookHistory(UUID.randomUUID(),h.userId(),h.recipeId(),h.recipeName(),h.portions(),Instant.now());});
  var result=service.cook(recipe.id(),new BigDecimal("2"));verify(inventory).consumeUpToAvailable(eq(rp.id()),argThat(v->v.compareTo(new BigDecimal("400"))==0));verify(inventory,never()).consumeUpToAvailable(eq(sp.id()),any());assertThat(result.warnings()).anyMatch(w->w.contains("100 g"));assertThat(result.history().recipeName()).isEqualTo("Mad");
 }

 private Recipe recipe(String name,RecipeIngredient...ingredients){return new Recipe(UUID.randomUUID(),user,name,null,Instant.now(),Instant.now(),List.of(ingredients),List.of());}
 private RecipeIngredient ingredient(ProductTemplate template,String quantity,RecipeUnit unit){return new RecipeIngredient(UUID.randomUUID(),template,new BigDecimal(quantity),unit,null,1);}
 private Product product(ProductTemplate template,InventoryTrackingMode mode){return new Product(UUID.randomUUID(),user,template.id(),template.name(),template.category(),template.defaultUnit(),mode);}
}
