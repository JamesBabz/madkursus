package dk.jamesbabz.madkursus.service.applications;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import dk.jamesbabz.madkursus.service.exceptions.*;
import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.ports.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecipeService {
    private static final String ACTIVE_PLAN_MESSAGE="Opskriften er stadig med i en aktiv madplan. Fjern den fra madplanen først.";
    private final RecipePort port;
    private final ProductTemplateService templates;
    private final CurrentUserProvider currentUser;
    private final MealPlanPort mealPlans;
    private final CookingProcessService processes;

    public record IngredientInput(UUID id,UUID templateId,BigDecimal quantity,RecipeUnit unit,String preparation,int sortOrder) {
        public IngredientInput(UUID templateId,BigDecimal quantity,RecipeUnit unit,String preparation,int sortOrder) {
            this(null,templateId,quantity,unit,preparation,sortOrder);
        }
    }
    public record BindingInput(String parameterKey,UUID recipeIngredientId,UUID productTemplateId,
            BigDecimal quantity,RecipeUnit unit,Integer durationSeconds,Integer temperatureCelsius,
            HeatLevel heatLevel,BigDecimal number,String text) {
        public BindingInput(String parameterKey,UUID productTemplateId,BigDecimal quantity,RecipeUnit unit,
                Integer durationSeconds,Integer temperatureCelsius,HeatLevel heatLevel,BigDecimal number,String text) {
            this(parameterKey,null,productTemplateId,quantity,unit,durationSeconds,temperatureCelsius,heatLevel,number,text);
        }
    }
    public record StepInput(RecipeStepType type,String instruction,int sortOrder,UUID cookingProcessId,List<BindingInput> parameterBindings) {
        public StepInput(String instruction,int sortOrder){this(RecipeStepType.TEXT,instruction,sortOrder,null,List.of());}
    }

    public List<Recipe> getAll(){return port.findAllByUserId(currentUser.currentUserId()).stream().map(this::render).toList();}
    public Recipe get(UUID id){return render(port.findByIdAndUserId(id,currentUser.currentUserId()).orElseThrow(()->new ResourceNotFoundException("Recipe",id)));}

    @Transactional
    public Recipe create(String name,String description,List<IngredientInput> ingredients,List<StepInput> steps){return persist(null,null,name,description,ingredients,steps,null);}

    @Transactional
    public Recipe createFromTemplate(RecipeTemplate template) {
        Instant now=Instant.now(); Map<UUID,RecipeIngredient> copiedIngredients=new HashMap<>();
        List<RecipeIngredient> ingredients=template.ingredients().stream().map(source->{
            RecipeIngredient copy=new RecipeIngredient(UUID.randomUUID(),source.productTemplate(),source.quantity(),source.unit(),source.preparation(),source.sortOrder());
            copiedIngredients.put(source.id(),copy); return copy;
        }).toList();
        List<RecipeStep> steps=template.steps().stream().map(source->new RecipeStep(null,source.type(),source.instruction(),source.sortOrder(),source.cookingProcessId(),
                source.parameterBindings().stream().map(binding->copyBinding(binding,copiedIngredients)).toList(),null)).toList();
        validateAllocations(ingredients,steps);
        return render(port.save(new Recipe(null,currentUser.currentUserId(),template.id(),template.name(),template.description(),now,now,ingredients,steps)));
    }

    @Transactional
    public Recipe update(UUID id,String name,String description,List<IngredientInput> ingredients,List<StepInput> steps){Recipe old=get(id);return persist(id,old.sourceTemplateId(),name,description,ingredients,steps,old.createdAt());}

    @Transactional
    public void delete(UUID id){Recipe recipe=get(id);UUID userId=recipe.userId();if(mealPlans.existsPlannedByRecipeIdAndUserId(id,userId))throw new ConflictException(ACTIVE_PLAN_MESSAGE);mealPlans.detachHistoricalRecipeReferences(id,userId);try{port.deleteByIdAndUserId(id,userId);}catch(DataIntegrityViolationException exception){throw new ConflictException(ACTIVE_PLAN_MESSAGE);}}

    private Recipe persist(UUID id,UUID sourceTemplateId,String name,String description,List<IngredientInput> inputs,List<StepInput> stepInputs,Instant created) {
        if(name==null||name.isBlank())throw new InvalidInputException("Recipe name is required");
        validateOrders(inputs.stream().map(IngredientInput::sortOrder).toList()); validateOrders(stepInputs.stream().map(StepInput::sortOrder).toList());
        List<RecipeIngredient> ingredients=inputs.stream().map(input->{
            if(input.templateId()==null||input.quantity()==null||input.quantity().signum()<=0||input.unit()==null)throw new InvalidInputException("Ingredient template, quantity and unit are required");
            return new RecipeIngredient(Objects.requireNonNullElseGet(input.id(),UUID::randomUUID),templates.get(input.templateId()),input.quantity(),input.unit(),blankToNull(input.preparation()),input.sortOrder());
        }).sorted(Comparator.comparingInt(RecipeIngredient::sortOrder)).toList();
        if(ingredients.stream().map(RecipeIngredient::id).distinct().count()!=ingredients.size())throw new InvalidInputException("Recipe ingredient IDs must be unique");
        Map<UUID,RecipeIngredient> byId=ingredients.stream().collect(Collectors.toMap(RecipeIngredient::id,Function.identity()));
        List<RecipeStep> steps=stepInputs.stream().map(input->step(input,byId)).sorted(Comparator.comparingInt(RecipeStep::sortOrder)).toList();
        validateAllocations(ingredients,steps);
        Instant now=Instant.now(); return render(port.save(new Recipe(id,currentUser.currentUserId(),sourceTemplateId,name.trim(),blankToNull(description),created==null?now:created,now,ingredients,steps)));
    }

    private RecipeStep step(StepInput input,Map<UUID,RecipeIngredient> ingredients) {
        RecipeStepType type=Objects.requireNonNullElse(input.type(),RecipeStepType.TEXT);
        if(type==RecipeStepType.TEXT){if(input.instruction()==null||input.instruction().isBlank())throw new InvalidInputException("Step instruction is required");return new RecipeStep(null,input.instruction().trim(),input.sortOrder());}
        if(input.cookingProcessId()==null)throw new InvalidInputException("Cooking process is required");
        CookingProcess process=processes.get(input.cookingProcessId());
        List<CookingProcessBinding> bindings=Objects.requireNonNullElse(input.parameterBindings(),List.<BindingInput>of()).stream().map(value->binding(value,ingredients)).toList();
        processes.validateBindings(process,bindings); return new RecipeStep(null,RecipeStepType.PROCESS,null,input.sortOrder(),process.id(),bindings,null);
    }

    private CookingProcessBinding binding(BindingInput input,Map<UUID,RecipeIngredient> ingredients) {
        RecipeIngredient ingredient=input.recipeIngredientId()==null?null:ingredients.get(input.recipeIngredientId());
        if(input.recipeIngredientId()!=null&&ingredient==null)throw new InvalidInputException("Process binding references an ingredient outside the recipe");
        if(ingredient!=null&&input.productTemplateId()!=null&&!ingredient.productTemplate().id().equals(input.productTemplateId()))throw new InvalidInputException("Process ingredient and product template do not match");
        ProductTemplate product=ingredient!=null?ingredient.productTemplate():(input.productTemplateId()==null?null:templates.get(input.productTemplateId()));
        return new CookingProcessBinding(null,input.parameterKey(),input.recipeIngredientId(),product,new CookingProcessValue(input.quantity(),input.unit(),input.durationSeconds(),input.temperatureCelsius(),input.heatLevel(),input.number(),blankToNull(input.text())));
    }

    private CookingProcessBinding copyBinding(CookingProcessBinding source,Map<UUID,RecipeIngredient> copiedIngredients) {
        RecipeIngredient ingredient=source.recipeIngredientId()==null?null:copiedIngredients.get(source.recipeIngredientId());
        if(source.recipeIngredientId()!=null&&ingredient==null)throw new InvalidInputException("Recipe template process binding references an unknown ingredient");
        return new CookingProcessBinding(null,source.parameterKey(),ingredient==null?null:ingredient.id(),ingredient==null?source.productTemplate():ingredient.productTemplate(),source.value());
    }

    private void validateAllocations(List<RecipeIngredient> ingredients,List<RecipeStep> steps) {
        Map<UUID,RecipeIngredient> byId=ingredients.stream().collect(Collectors.toMap(RecipeIngredient::id,Function.identity()));
        Map<UUID,BigDecimal> allocated=new HashMap<>();
        for(RecipeStep step:steps) for(CookingProcessBinding binding:step.parameterBindings()) if(binding.recipeIngredientId()!=null) {
            RecipeIngredient ingredient=byId.get(binding.recipeIngredientId());
            if(ingredient==null)throw new InvalidInputException("Process binding references an ingredient outside the recipe");
            BigDecimal amount=toBase(binding.value().quantity(),binding.value().unit());
            if(!dimension(binding.value().unit()).equals(dimension(ingredient.unit())))throw new InvalidInputException("Process allocation unit is not compatible with recipe ingredient: "+ingredient.productTemplate().name());
            allocated.merge(ingredient.id(),amount,BigDecimal::add);
        }
        for(RecipeIngredient ingredient:ingredients) if(allocated.getOrDefault(ingredient.id(),BigDecimal.ZERO).compareTo(toBase(ingredient.quantity(),ingredient.unit()))>0)
            throw new InvalidInputException("Process allocations exceed recipe ingredient quantity: "+ingredient.productTemplate().name());
    }

    private String dimension(RecipeUnit unit){if(unit==null)throw new InvalidInputException("Process ingredient unit is required");return switch(unit){case GRAM->"MASS";case PIECE->"COUNT";case MILLILITER,TEASPOON,TABLESPOON,DECILITER->"VOLUME";};}
    private BigDecimal toBase(BigDecimal value,RecipeUnit unit){if(value==null||value.signum()<=0)throw new InvalidInputException("Process ingredient quantity must be positive");BigDecimal factor=switch(unit){case GRAM,MILLILITER,PIECE->BigDecimal.ONE;case TEASPOON->new BigDecimal("5");case TABLESPOON->new BigDecimal("15");case DECILITER->new BigDecimal("100");};return value.multiply(factor);}
    private Recipe render(Recipe recipe){if(recipe.steps().stream().noneMatch(s->s.type()==RecipeStepType.PROCESS))return recipe;return new Recipe(recipe.id(),recipe.userId(),recipe.sourceTemplateId(),recipe.name(),recipe.description(),recipe.createdAt(),recipe.updatedAt(),recipe.ingredients(),recipe.steps().stream().map(s->s.type()==RecipeStepType.PROCESS?new RecipeStep(s.id(),s.type(),null,s.sortOrder(),s.cookingProcessId(),s.parameterBindings(),processes.render(s.cookingProcessId(),s.parameterBindings())):s).toList());}
    private void validateOrders(List<Integer> values){if(values.stream().anyMatch(v->v==null||v<=0)||new HashSet<>(values).size()!=values.size())throw new InvalidInputException("Sort order must be positive and unique");}
    private String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
}
