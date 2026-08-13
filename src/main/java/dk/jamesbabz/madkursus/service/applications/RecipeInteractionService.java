package dk.jamesbabz.madkursus.service.applications;

import java.math.BigDecimal; import java.time.Instant; import java.util.*;
import dk.jamesbabz.madkursus.service.exceptions.InvalidInputException; import dk.jamesbabz.madkursus.service.models.*; import dk.jamesbabz.madkursus.service.ports.*;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class RecipeInteractionService {
 private final RecipeService recipeService; private final ProductService productService; private final InventoryService inventoryService; private final ShoppingListService shoppingListService; private final RecipeCookHistoryPort historyPort; private final CurrentUserProvider currentUser;
 private record Aggregate(ProductTemplate template,BigDecimal quantity,Unit unit,String warning){}

 public RecipeRequirementCalculation calculate(List<RecipeSelection> selections){
  if(selections==null||selections.isEmpty())throw new InvalidInputException("Select at least one recipe");
  Map<UUID,Aggregate> totals=new LinkedHashMap<>();
  for(RecipeSelection selection:selections){if(selection.recipeId()==null||selection.portions()==null||selection.portions().signum()<=0)throw new InvalidInputException("Recipe portions must be greater than zero"); Recipe recipe=recipeService.get(selection.recipeId());
   for(RecipeIngredient ingredient:recipe.ingredients()){ProductTemplate template=ingredient.productTemplate();BigDecimal scaled=ingredient.quantity().multiply(selection.portions()); Normalized normalized=normalize(scaled,ingredient.unit(),template.defaultUnit()); Aggregate old=totals.get(template.id());
    if(old==null)totals.put(template.id(),new Aggregate(template,normalized.quantity,normalized.unit,normalized.warning));
    else if(old.warning!=null||normalized.warning!=null)totals.put(template.id(),new Aggregate(template,null,template.defaultUnit(),old.warning!=null?old.warning:normalized.warning));
    else totals.put(template.id(),new Aggregate(template,old.quantity.add(normalized.quantity),old.unit,null));
   }
  }
  Map<UUID,InventoryItem> inventory=new HashMap<>(); inventoryService.getAll().forEach(i->inventory.put(i.product().id(),i));
  List<RecipeRequirement> results=new ArrayList<>();
  for(Aggregate a:totals.values()){Optional<Product> product=productService.findEquivalent(a.template.id(),a.template.name());InventoryTrackingMode mode=product.map(Product::inventoryTrackingMode).orElse(a.template.defaultTrackingMode()); InventoryItem stock=product.map(p->inventory.get(p.id())).orElse(null);
   if(a.warning!=null){results.add(new RecipeRequirement(a.template,product.orElse(null),mode,null,a.unit,null,null,false,a.warning));continue;}
   if(mode==InventoryTrackingMode.PRESENCE){boolean present=stock!=null;results.add(new RecipeRequirement(a.template,product.orElse(null),mode,a.quantity,a.unit,null,null,present,null));continue;}
   BigDecimal available=stock==null||stock.quantity()==null?BigDecimal.ZERO:stock.quantity();BigDecimal rawMissing=a.quantity.subtract(available).max(BigDecimal.ZERO);BigDecimal missing=roundUp(rawMissing,a.unit);results.add(new RecipeRequirement(a.template,product.orElse(null),mode,a.quantity,a.unit,available,missing,missing.signum()==0,null));
  }
  return new RecipeRequirementCalculation(List.copyOf(results));
 }

 @Transactional public RecipeRequirementCalculation addMissingToShoppingList(List<RecipeSelection> selections){RecipeRequirementCalculation calculation=calculate(selections);for(RecipeRequirement r:calculation.requirements()){if(r.warning()!=null||r.satisfied())continue;if(r.trackingMode()==InventoryTrackingMode.PRESENCE)shoppingListService.ensureAtLeastFromTemplate(r.productTemplate().id(),null);else shoppingListService.ensureAtLeastFromTemplate(r.productTemplate().id(),QuantityRoundingPolicy.forShoppingList(r.missingQuantity()));}return calculation;}

 @Transactional public RecipeCookResult cook(UUID recipeId,BigDecimal portions){Recipe recipe=recipeService.get(recipeId);RecipeRequirementCalculation calculation=calculate(List.of(new RecipeSelection(recipeId,portions)));List<String>warnings=new ArrayList<>();for(RecipeRequirement r:calculation.requirements()){if(r.warning()!=null){warnings.add(r.productTemplate().name()+": "+r.warning());continue;}if(r.trackingMode()==InventoryTrackingMode.PRESENCE){if(!r.satisfied())warnings.add(r.productTemplate().name()+" var ikke registreret som på lager");continue;}BigDecimal amount=roundUp(r.requiredQuantity(),r.unit());if(r.product()==null){warnings.add(format(amount,r.unit())+" "+r.productTemplate().name()+" blev brugt ud over registreret lager");continue;}InventoryService.Consumption c=inventoryService.consumeUpToAvailable(r.product().id(),amount);if(c.shortage().signum()>0)warnings.add(format(c.shortage(),r.unit())+" "+r.productTemplate().name()+" blev brugt ud over registreret lager");}
  RecipeCookHistory history=historyPort.save(new RecipeCookHistory(null,currentUser.currentUserId(),recipe.id(),recipe.name(),portions,Instant.now()));return new RecipeCookResult(recipe,portions,history,calculation.requirements(),List.copyOf(warnings));}

 private record Normalized(BigDecimal quantity,Unit unit,String warning){}
 private Normalized normalize(BigDecimal value,RecipeUnit recipeUnit,Unit storage){if(storage==Unit.GRAM&&recipeUnit==RecipeUnit.GRAM)return new Normalized(value,Unit.GRAM,null);if(storage==Unit.PIECE&&recipeUnit==RecipeUnit.PIECE)return new Normalized(value,Unit.PIECE,null);if(storage==Unit.MILLILITER){var ml=recipeUnit.toMilliliters(value);if(ml.isPresent())return new Normalized(ml.get(),Unit.MILLILITER,null);}return new Normalized(null,storage,"Opskriften bruger "+recipeUnit.name()+", men lageret føres i "+storage.name()+". Kan ikke beregnes automatisk.");}
 public BigDecimal roundUp(BigDecimal value,Unit unit){return QuantityRoundingPolicy.forInventory(value,unit);}
 private String format(BigDecimal amount,Unit unit){return amount.toPlainString()+" "+(unit==Unit.GRAM?"g":unit==Unit.MILLILITER?"ml":"stk");}
}
