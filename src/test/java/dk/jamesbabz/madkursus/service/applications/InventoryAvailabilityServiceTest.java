package dk.jamesbabz.madkursus.service.applications;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.ports.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryAvailabilityServiceTest {
 @Mock InventoryPort inventory; @Mock MealPlanPort plans; @Mock CurrentUserProvider currentUser;
 UUID user,otherUser; ProductTemplate eggs,salt,milk; Product eggProduct,saltProduct; InventoryAvailabilityService service;
 @BeforeEach void setup(){user=UUID.randomUUID();otherUser=UUID.randomUUID();when(currentUser.currentUserId()).thenReturn(user);eggs=template("Æg",Unit.PIECE,InventoryTrackingMode.QUANTITY);salt=template("Salt",Unit.GRAM,InventoryTrackingMode.PRESENCE);milk=template("Mælk",Unit.MILLILITER,InventoryTrackingMode.QUANTITY);eggProduct=product(eggs,InventoryTrackingMode.QUANTITY);saltProduct=product(salt,InventoryTrackingMode.PRESENCE);lenient().when(inventory.findAllByUserId(user)).thenReturn(List.of(new InventoryItem(UUID.randomUUID(),eggProduct,new BigDecimal("10"))));service=new InventoryAvailabilityService(inventory,plans,currentUser,new RecipeQuantityNormalizer());}

 @Test void globallyAggregatesPlansScalesPortionsAndNeverMakesAvailabilityNegative(){
  MealPlan a=plan("A",planned(recipe("Frikadeller",ingredient(eggs,"2",RecipeUnit.PIECE)),4,PlannedRecipeStatus.PLANNED));
  MealPlan b=plan("B",planned(recipe("Æggekage",ingredient(eggs,"3",RecipeUnit.PIECE)),2,PlannedRecipeStatus.PLANNED));
  when(plans.findAllByUserId(user)).thenReturn(List.of(a,b));var result=service.forTemplate(service.snapshot(null),eggs,eggProduct,InventoryTrackingMode.QUANTITY);
  assertThat(result.reservedQuantity()).isEqualByComparingTo("14");assertThat(result.availableQuantity()).isZero();assertThat(result.plannedShortfall()).isEqualByComparingTo("4");assertThat(result.reservations()).hasSize(2);
  verify(plans).findAllByUserId(user);verify(plans,never()).findAllByUserId(otherUser);
 }
 @Test void exclusionMakesCompetingPlansSeeOnlyOtherReservations(){
  MealPlan a=plan("A",planned(recipe("A-ret",ingredient(eggs,"8",RecipeUnit.PIECE)),1,PlannedRecipeStatus.PLANNED));MealPlan b=plan("B",planned(recipe("B-ret",ingredient(eggs,"6",RecipeUnit.PIECE)),1,PlannedRecipeStatus.PLANNED));when(plans.findAllByUserId(user)).thenReturn(List.of(a,b));
  var forA=service.forTemplate(service.snapshot(a.id()),eggs,eggProduct,InventoryTrackingMode.QUANTITY);var forB=service.forTemplate(service.snapshot(b.id()),eggs,eggProduct,InventoryTrackingMode.QUANTITY);
  assertThat(forA.reservedQuantity()).isEqualByComparingTo("6");assertThat(forA.availableQuantity()).isEqualByComparingTo("4");assertThat(forB.reservedQuantity()).isEqualByComparingTo("8");assertThat(forB.availableQuantity()).isEqualByComparingTo("2");
 }
 @Test void cookedAndSkippedDoNotReserveAndChangingStateOrPortionsImmediatelyChangesResult(){
  Recipe recipe=recipe("Ret",ingredient(eggs,"1.5",RecipeUnit.PIECE));MealPlan initial=plan("Plan",planned(recipe,2,PlannedRecipeStatus.PLANNED));when(plans.findAllByUserId(user)).thenReturn(List.of(initial),List.of(plan("Plan",planned(recipe,4,PlannedRecipeStatus.PLANNED))),List.of(plan("Plan",planned(recipe,4,PlannedRecipeStatus.SKIPPED))),List.of(plan("Plan",planned(recipe,4,PlannedRecipeStatus.COOKED))),List.of());
  assertThat(service.snapshot(null).reservation(eggs.id()).quantity()).isEqualByComparingTo("3");assertThat(service.snapshot(null).reservation(eggs.id()).quantity()).isEqualByComparingTo("6");assertThat(service.snapshot(null).reservation(eggs.id()).quantity()).isZero();assertThat(service.snapshot(null).reservation(eggs.id()).quantity()).isZero();assertThat(service.snapshot(null).reservation(eggs.id()).quantity()).isZero();
 }
 @Test void presenceRemainsAvailableAndCountsPlannedRecipeUsesWithoutFakeQuantity(){
  Recipe one=recipe("A",ingredient(salt,"2",RecipeUnit.GRAM)),two=recipe("B",ingredient(salt,"1",RecipeUnit.GRAM));when(inventory.findAllByUserId(user)).thenReturn(List.of(new InventoryItem(UUID.randomUUID(),saltProduct,null)));when(plans.findAllByUserId(user)).thenReturn(List.of(plan("Plan",planned(one,1,PlannedRecipeStatus.PLANNED),planned(two,1,PlannedRecipeStatus.PLANNED))));
  var result=service.forTemplate(service.snapshot(null),salt,saltProduct,InventoryTrackingMode.PRESENCE);assertThat(result.physicalQuantity()).isNull();assertThat(result.reservedQuantity()).isNull();assertThat(result.plannedUsageCount()).isEqualTo(2);assertThat(result.reservations()).hasSize(2);
 }
 @Test void reservationsReuseVolumeNormalization(){
  Product milkProduct=product(milk,InventoryTrackingMode.QUANTITY);when(plans.findAllByUserId(user)).thenReturn(List.of(plan("Plan",planned(recipe("Drik",ingredient(milk,"1",RecipeUnit.DECILITER),ingredient(milk,"2",RecipeUnit.TABLESPOON),ingredient(milk,"1",RecipeUnit.TEASPOON)),1,PlannedRecipeStatus.PLANNED))));
  assertThat(service.forTemplate(service.snapshot(null),milk,milkProduct,InventoryTrackingMode.QUANTITY).reservedQuantity()).isEqualByComparingTo("135");
 }
 @Test void untrackedIngredientsNeverCreateMealPlanReservations(){
  ProductTemplate water=template("Vand",Unit.MILLILITER,InventoryTrackingMode.UNTRACKED);when(plans.findAllByUserId(user)).thenReturn(List.of(plan("Plan",planned(recipe("Sovs",ingredient(water,"0.5",RecipeUnit.DECILITER)),4,PlannedRecipeStatus.PLANNED))));
  var snapshot=service.snapshot(null);assertThat(snapshot.reservation(water.id()).quantity()).isZero();assertThat(snapshot.reservationsByTemplateId()).doesNotContainKey(water.id());
 }
 private ProductTemplate template(String name,Unit unit,InventoryTrackingMode mode){return new ProductTemplate(UUID.randomUUID(),name,ProductCategory.OTHER,unit,mode,List.of(),false);}
 private Product product(ProductTemplate t,InventoryTrackingMode mode){return new Product(UUID.randomUUID(),user,t.id(),t.name(),t.category(),t.defaultUnit(),mode);}
 private RecipeIngredient ingredient(ProductTemplate t,String q,RecipeUnit u){return new RecipeIngredient(UUID.randomUUID(),t,new BigDecimal(q),u,null,1);}
 private Recipe recipe(String name,RecipeIngredient...ingredients){return new Recipe(UUID.randomUUID(),user,name,null,Instant.now(),Instant.now(),List.of(ingredients),List.of());}
 private PlannedRecipe planned(Recipe r,int portions,PlannedRecipeStatus status){return new PlannedRecipe(UUID.randomUUID(),r,portions,1,status);}
 private MealPlan plan(String name,PlannedRecipe...recipes){return new MealPlan(UUID.randomUUID(),user,name,Instant.now(),Instant.now(),List.of(recipes));}
}
