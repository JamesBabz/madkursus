package dk.jamesbabz.madkursus.service.applications;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import dk.jamesbabz.madkursus.service.exceptions.InvalidInputException;
import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.ports.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class RecipeInteractionService {
    private final RecipeService recipeService; private final ProductService productService;
    private final InventoryService inventoryService; private final ShoppingListService shoppingListService;
    private final RecipeCookHistoryPort historyPort; private final CurrentUserProvider currentUser;
    private final InventoryAvailabilityService availabilityService; private final RecipeQuantityNormalizer normalizer;
    private record Aggregate(ProductTemplate template,BigDecimal quantity,Unit unit,String warning,BigDecimal displayQuantity,RecipeUnit displayUnit){}

    public RecipeRequirementCalculation calculate(List<RecipeSelection> selections){return calculate(selections,null);}
    public RecipeRequirementCalculation calculate(List<RecipeSelection> selections,UUID excludedMealPlanId){
        if(selections==null||selections.isEmpty())throw new InvalidInputException("Select at least one recipe");
        Map<UUID,Aggregate> totals=new LinkedHashMap<>();
        for(RecipeSelection selection:selections){
            if(selection.recipeId()==null||selection.portions()==null||selection.portions().signum()<=0)throw new InvalidInputException("Recipe portions must be greater than zero");
            Recipe recipe=recipeService.get(selection.recipeId());
            for(RecipeIngredient ingredient:recipe.ingredients()){
                ProductTemplate template=ingredient.productTemplate(); BigDecimal scaled=ingredient.quantity().multiply(selection.portions());
                if(template.defaultTrackingMode()==InventoryTrackingMode.UNTRACKED)continue;
                NormalizedRecipeQuantity normalized=normalizer.normalize(scaled,ingredient.unit(),template); Aggregate old=totals.get(template.id());
                if(old==null)totals.put(template.id(),new Aggregate(template,normalized.quantity(),normalized.unit(),normalized.warning(),scaled,ingredient.unit()));
                else if(old.warning()!=null||normalized.warning()!=null)totals.put(template.id(),new Aggregate(template,null,template.defaultUnit(),old.warning()!=null?old.warning():normalized.warning(),sameDisplayUnit(old,ingredient.unit())?old.displayQuantity().add(scaled):null,sameDisplayUnit(old,ingredient.unit())?ingredient.unit():null));
                else totals.put(template.id(),new Aggregate(template,old.quantity().add(normalized.quantity()),old.unit(),null,sameDisplayUnit(old,ingredient.unit())?old.displayQuantity().add(scaled):null,sameDisplayUnit(old,ingredient.unit())?ingredient.unit():null));
            }
        }
        InventoryAvailabilityService.Snapshot snapshot=availabilityService.snapshot(excludedMealPlanId); List<RecipeRequirement> results=new ArrayList<>();
        for(Aggregate aggregate:totals.values()){
            Optional<Product> product=productService.findEquivalent(aggregate.template().id(),aggregate.template().name());
            InventoryTrackingMode mode=product.map(Product::inventoryTrackingMode).orElse(aggregate.template().defaultTrackingMode());
            var availability=availabilityService.forTemplate(snapshot,aggregate.template(),product.orElse(null),mode);
            if(mode==InventoryTrackingMode.PRESENCE){boolean present=product.map(p->snapshot.inventoryByProductId().containsKey(p.id())).orElse(false);results.add(new RecipeRequirement(aggregate.template(),product.orElse(null),mode,aggregate.quantity(),aggregate.unit(),null,null,null,null,null,availability.plannedUsageCount(),availability.reservations(),present,null,aggregate.displayQuantity(),aggregate.displayUnit()));continue;}
            if(aggregate.warning()!=null){results.add(new RecipeRequirement(aggregate.template(),product.orElse(null),mode,null,aggregate.unit(),null,null,null,null,null,availability.plannedUsageCount(),availability.reservations(),false,aggregate.warning(),aggregate.displayQuantity(),aggregate.displayUnit()));continue;}
            BigDecimal rawMissing=aggregate.quantity().subtract(availability.availableQuantity()).max(BigDecimal.ZERO);
            BigDecimal missing=roundUp(rawMissing,aggregate.unit());
            results.add(new RecipeRequirement(aggregate.template(),product.orElse(null),mode,aggregate.quantity(),aggregate.unit(),
                    availability.physicalQuantity(),availability.reservedQuantity(),availability.availableQuantity(),availability.plannedShortfall(),missing,
                    availability.plannedUsageCount(),availability.reservations(),missing.signum()==0,null,aggregate.displayQuantity(),aggregate.displayUnit()));
        }
        return new RecipeRequirementCalculation(List.copyOf(results));
    }

    @Transactional public RecipeRequirementCalculation addMissingToShoppingList(List<RecipeSelection> selections){return addMissingToShoppingList(selections,null);}
    @Transactional public RecipeRequirementCalculation addMissingToShoppingList(List<RecipeSelection> selections,UUID excludedMealPlanId){
        RecipeRequirementCalculation calculation=calculate(selections,excludedMealPlanId);
        for(RecipeRequirement requirement:calculation.requirements()){
            if(requirement.trackingMode()==InventoryTrackingMode.UNTRACKED)continue;
            if(requirement.warning()!=null||requirement.satisfied())continue;
            if(requirement.trackingMode()==InventoryTrackingMode.PRESENCE)shoppingListService.ensureAtLeastFromTemplate(requirement.productTemplate().id(),null);
            else shoppingListService.ensureAtLeastFromTemplate(requirement.productTemplate().id(),QuantityRoundingPolicy.forShoppingList(requirement.missingQuantity()));
        }
        return calculation;
    }

    @Transactional public RecipeCookResult cook(UUID recipeId,BigDecimal portions){return cook(recipeId,portions,null);}
    @Transactional public RecipeCookResult cook(UUID recipeId,BigDecimal portions,UUID excludedMealPlanId){
        Recipe recipe=recipeService.get(recipeId); RecipeRequirementCalculation calculation=calculate(List.of(new RecipeSelection(recipeId,portions)),excludedMealPlanId); List<String>warnings=new ArrayList<>();
        for(RecipeRequirement requirement:calculation.requirements()){
            if(requirement.trackingMode()==InventoryTrackingMode.UNTRACKED)continue;
            if(requirement.warning()!=null){warnings.add(requirement.productTemplate().name()+": "+requirement.warning());continue;}
            if(requirement.trackingMode()==InventoryTrackingMode.PRESENCE){if(!requirement.satisfied())warnings.add(requirement.productTemplate().name()+" var ikke registreret som på lager");continue;}
            BigDecimal amount=roundUp(requirement.requiredQuantity(),requirement.unit());
            if(requirement.product()==null){warnings.add(format(amount,requirement.unit())+" "+requirement.productTemplate().name()+" blev brugt ud over registreret lager");continue;}
            InventoryService.Consumption consumption=inventoryService.consumeUpToAvailable(requirement.product().id(),amount);
            if(consumption.shortage().signum()>0)warnings.add(format(consumption.shortage(),requirement.unit())+" "+requirement.productTemplate().name()+" blev brugt ud over registreret lager");
        }
        RecipeCookHistory history=historyPort.save(new RecipeCookHistory(null,currentUser.currentUserId(),recipe.id(),recipe.name(),portions,Instant.now()));
        return new RecipeCookResult(recipe,portions,history,calculation.requirements(),List.copyOf(warnings));
    }
    public BigDecimal roundUp(BigDecimal value,Unit unit){return QuantityRoundingPolicy.forInventory(value,unit);}
    private boolean sameDisplayUnit(Aggregate aggregate,RecipeUnit unit){return aggregate.displayUnit()==unit&&aggregate.displayQuantity()!=null;}
    private String format(BigDecimal amount,Unit unit){return amount.toPlainString()+" "+(unit==Unit.GRAM?"g":unit==Unit.MILLILITER?"ml":"stk");}
}
