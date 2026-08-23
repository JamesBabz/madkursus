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
            HeatLevel heatLevel,BigDecimal number,String text,UUID preparedComponentId) {
        public BindingInput(String parameterKey,UUID recipeIngredientId,UUID productTemplateId,BigDecimal quantity,RecipeUnit unit,Integer durationSeconds,Integer temperatureCelsius,HeatLevel heatLevel,BigDecimal number,String text){this(parameterKey,recipeIngredientId,productTemplateId,quantity,unit,durationSeconds,temperatureCelsius,heatLevel,number,text,null);}
        public BindingInput(String parameterKey,UUID productTemplateId,BigDecimal quantity,RecipeUnit unit,
                Integer durationSeconds,Integer temperatureCelsius,HeatLevel heatLevel,BigDecimal number,String text) {
            this(parameterKey,null,productTemplateId,quantity,unit,durationSeconds,temperatureCelsius,heatLevel,number,text,null);
        }
    }
    public record ComponentIngredientInput(UUID id,UUID recipeIngredientId,BigDecimal quantity,RecipeUnit unit,int sortOrder){}
    public record ComponentInput(UUID id,String key,String name,int sortOrder,List<ComponentIngredientInput> ingredients,List<PreparationInput> preparationSteps){}
    public record StepInput(RecipeStepType type,String instruction,int sortOrder,UUID cookingProcessId,List<BindingInput> parameterBindings) {
        public StepInput(String instruction,int sortOrder){this(RecipeStepType.TEXT,instruction,sortOrder,null,List.of());}
    }
    public record PreparationInput(UUID id,String instruction,int sortOrder){}
    public record EquipmentInput(UUID id,EquipmentType equipmentType,String label,int sortOrder){}

    public List<Recipe> getAll(){return port.findAllByUserId(currentUser.currentUserId()).stream().map(this::render).toList();}
    public Recipe get(UUID id){return get(id,BigDecimal.ONE);}
    public Recipe get(UUID id,BigDecimal portions){if(portions==null||portions.signum()<=0)throw new InvalidInputException("Portions must be positive");return render(port.findByIdAndUserId(id,currentUser.currentUserId()).orElseThrow(()->new ResourceNotFoundException("Recipe",id)),portions);}

    @Transactional
    public Recipe create(String name,String description,List<IngredientInput> ingredients,List<StepInput> steps){return create(name,description,ingredients,steps,List.of(),List.of());}
    public Recipe create(String name,String description,List<IngredientInput> ingredients,List<StepInput> steps,List<PreparationInput> preparation,List<EquipmentInput> equipment){return persist(null,null,name,description,ingredients,steps,preparation,equipment,null);}
    public Recipe create(String name,String description,List<IngredientInput> ingredients,List<StepInput> steps,List<PreparationInput> preparation,List<EquipmentInput> equipment,List<ComponentInput> components){return persist(null,null,name,description,ingredients,steps,preparation,equipment,null,components);}

    @Transactional
    public Recipe createFromTemplate(RecipeTemplate template) {
        Instant now=Instant.now(); Map<UUID,RecipeIngredient> copiedIngredients=new HashMap<>();
        List<RecipeIngredient> ingredients=template.ingredients().stream().map(source->{
            RecipeIngredient copy=new RecipeIngredient(UUID.randomUUID(),source.productTemplate(),source.quantity(),source.unit(),source.preparation(),source.sortOrder());
            copiedIngredients.put(source.id(),copy); return copy;
        }).toList();
        Map<UUID,PreparedComponent> copiedComponents=new HashMap<>();List<PreparedComponent> components=template.preparedComponents().stream().map(source->{PreparedComponent copy=new PreparedComponent(UUID.randomUUID(),source.key(),source.name(),source.sortOrder(),source.ingredients().stream().map(value->{RecipeIngredient ingredient=copiedIngredients.get(value.recipeIngredientId());return new PreparedComponentIngredient(UUID.randomUUID(),ingredient.id(),ingredient.productTemplate(),value.quantity(),value.unit(),value.sortOrder());}).toList(),source.preparationSteps().stream().map(value->new RecipePreparationStep(UUID.randomUUID(),value.instruction(),value.sortOrder())).toList());copiedComponents.put(source.id(),copy);return copy;}).toList();
        List<RecipeStep> steps=template.steps().stream().map(source->new RecipeStep(null,source.type(),source.instruction(),source.sortOrder(),source.cookingProcessId(),
                source.parameterBindings().stream().map(binding->copyBinding(binding,copiedIngredients,copiedComponents)).toList(),null)).toList();
        validateAllocations(ingredients,steps,components);
        List<RecipePreparationStep> preparation=template.preparationSteps().stream().map(value->new RecipePreparationStep(UUID.randomUUID(),value.instruction(),value.sortOrder())).toList();
        List<RecipeEquipmentRequirement> equipment=template.equipmentRequirements().stream().map(value->new RecipeEquipmentRequirement(UUID.randomUUID(),value.equipmentType(),value.label(),value.sortOrder())).toList();
        return render(port.save(new Recipe(null,currentUser.currentUserId(),template.id(),template.name(),template.description(),now,now,ingredients,steps,preparation,equipment,List.of(),components)));
    }

    @Transactional
    public Recipe update(UUID id,String name,String description,List<IngredientInput> ingredients,List<StepInput> steps){Recipe old=get(id);return persist(id,old.sourceTemplateId(),name,description,ingredients,steps,old.preparationSteps().stream().map(value->new PreparationInput(value.id(),value.instruction(),value.sortOrder())).toList(),old.equipmentRequirements().stream().map(value->new EquipmentInput(value.id(),value.equipmentType(),value.label(),value.sortOrder())).toList(),old.createdAt());}
    public Recipe update(UUID id,String name,String description,List<IngredientInput> ingredients,List<StepInput> steps,List<PreparationInput> preparation,List<EquipmentInput> equipment){Recipe old=get(id);return persist(id,old.sourceTemplateId(),name,description,ingredients,steps,preparation,equipment,old.createdAt());}
    public Recipe update(UUID id,String name,String description,List<IngredientInput> ingredients,List<StepInput> steps,List<PreparationInput> preparation,List<EquipmentInput> equipment,List<ComponentInput> components){Recipe old=get(id);return persist(id,old.sourceTemplateId(),name,description,ingredients,steps,preparation,equipment,old.createdAt(),components);}

    @Transactional
    public void delete(UUID id){Recipe recipe=get(id);UUID userId=recipe.userId();if(mealPlans.existsPlannedByRecipeIdAndUserId(id,userId))throw new ConflictException(ACTIVE_PLAN_MESSAGE);mealPlans.detachHistoricalRecipeReferences(id,userId);try{port.deleteByIdAndUserId(id,userId);}catch(DataIntegrityViolationException exception){throw new ConflictException(ACTIVE_PLAN_MESSAGE);}}

    private Recipe persist(UUID id,UUID sourceTemplateId,String name,String description,List<IngredientInput> inputs,List<StepInput> stepInputs,List<PreparationInput> preparationInputs,List<EquipmentInput> equipmentInputs,Instant created) {return persist(id,sourceTemplateId,name,description,inputs,stepInputs,preparationInputs,equipmentInputs,created,List.of());}
    private Recipe persist(UUID id,UUID sourceTemplateId,String name,String description,List<IngredientInput> inputs,List<StepInput> stepInputs,List<PreparationInput> preparationInputs,List<EquipmentInput> equipmentInputs,Instant created,List<ComponentInput> componentInputs) {
        if(name==null||name.isBlank())throw new InvalidInputException("Recipe name is required");
        validateOrders(inputs.stream().map(IngredientInput::sortOrder).toList()); validateOrders(stepInputs.stream().map(StepInput::sortOrder).toList());
        List<RecipeIngredient> ingredients=inputs.stream().map(input->{
            if(input.templateId()==null||input.quantity()==null||input.quantity().signum()<=0||input.unit()==null)throw new InvalidInputException("Ingredient template, quantity and unit are required");
            return new RecipeIngredient(Objects.requireNonNullElseGet(input.id(),UUID::randomUUID),templates.get(input.templateId()),input.quantity(),input.unit(),blankToNull(input.preparation()),input.sortOrder());
        }).sorted(Comparator.comparingInt(RecipeIngredient::sortOrder)).toList();
        if(ingredients.stream().map(RecipeIngredient::id).distinct().count()!=ingredients.size())throw new InvalidInputException("Recipe ingredient IDs must be unique");
        Map<UUID,RecipeIngredient> byId=ingredients.stream().collect(Collectors.toMap(RecipeIngredient::id,Function.identity()));
        List<PreparedComponent> components=Objects.requireNonNullElse(componentInputs,List.<ComponentInput>of()).stream().map(input->component(input,byId)).sorted(Comparator.comparingInt(PreparedComponent::sortOrder)).toList();Map<UUID,PreparedComponent> componentsById=components.stream().collect(Collectors.toMap(PreparedComponent::id,Function.identity()));
        List<RecipeStep> steps=stepInputs.stream().map(input->step(input,byId,componentsById)).sorted(Comparator.comparingInt(RecipeStep::sortOrder)).toList();
        validateAllocations(ingredients,steps,components);
        List<RecipePreparationStep> preparation=preparationInputs.stream().map(value->{if(value.instruction()==null||value.instruction().isBlank()||value.sortOrder()<=0)throw new InvalidInputException("Preparation instruction and order are required");return new RecipePreparationStep(Objects.requireNonNullElseGet(value.id(),UUID::randomUUID),value.instruction().trim(),value.sortOrder());}).sorted(Comparator.comparingInt(RecipePreparationStep::sortOrder)).toList();
        List<RecipeEquipmentRequirement> equipment=equipmentInputs.stream().map(value->{if(value.sortOrder()<=0||(value.equipmentType()==null&&(value.label()==null||value.label().isBlank())))throw new InvalidInputException("Equipment type or label and order are required");return new RecipeEquipmentRequirement(Objects.requireNonNullElseGet(value.id(),UUID::randomUUID),value.equipmentType(),blankToNull(value.label()),value.sortOrder());}).sorted(Comparator.comparingInt(RecipeEquipmentRequirement::sortOrder)).toList();
        Instant now=Instant.now(); return render(port.save(new Recipe(id,currentUser.currentUserId(),sourceTemplateId,name.trim(),blankToNull(description),created==null?now:created,now,ingredients,steps,preparation,equipment,List.of(),components)));
    }

    private PreparedComponent component(ComponentInput input,Map<UUID,RecipeIngredient> ingredients){if(input.name()==null||input.name().isBlank()||input.key()==null||!input.key().matches("[A-Z][A-Z0-9_]*")||input.sortOrder()<=0)throw new InvalidInputException("Prepared component key, name and order are required");List<PreparedComponentIngredient> allocations=Objects.requireNonNullElse(input.ingredients(),List.<ComponentIngredientInput>of()).stream().map(value->{RecipeIngredient ingredient=ingredients.get(value.recipeIngredientId());if(ingredient==null)throw new InvalidInputException("Prepared component references an ingredient outside the recipe");if(value.quantity()==null||value.quantity().signum()<=0||value.unit()==null)throw new InvalidInputException("Prepared component allocation must be positive");return new PreparedComponentIngredient(Objects.requireNonNullElseGet(value.id(),UUID::randomUUID),ingredient.id(),ingredient.productTemplate(),value.quantity(),value.unit(),value.sortOrder());}).toList();List<RecipePreparationStep> prep=Objects.requireNonNullElse(input.preparationSteps(),List.<PreparationInput>of()).stream().map(v->new RecipePreparationStep(Objects.requireNonNullElseGet(v.id(),UUID::randomUUID),v.instruction(),v.sortOrder())).toList();return new PreparedComponent(Objects.requireNonNullElseGet(input.id(),UUID::randomUUID),input.key(),input.name().trim(),input.sortOrder(),allocations,prep);}

    private RecipeStep step(StepInput input,Map<UUID,RecipeIngredient> ingredients){return step(input,ingredients,Map.of());}
    private RecipeStep step(StepInput input,Map<UUID,RecipeIngredient> ingredients,Map<UUID,PreparedComponent> components) {
        RecipeStepType type=Objects.requireNonNullElse(input.type(),RecipeStepType.TEXT);
        if(type==RecipeStepType.TEXT){if(input.instruction()==null||input.instruction().isBlank())throw new InvalidInputException("Step instruction is required");return new RecipeStep(null,input.instruction().trim(),input.sortOrder());}
        if(input.cookingProcessId()==null)throw new InvalidInputException("Cooking process is required");
        CookingProcess process=processes.get(input.cookingProcessId());
        List<CookingProcessBinding> bindings=Objects.requireNonNullElse(input.parameterBindings(),List.<BindingInput>of()).stream().map(value->binding(value,ingredients,components)).toList();
        processes.validateBindings(process,bindings); return new RecipeStep(null,RecipeStepType.PROCESS,null,input.sortOrder(),process.id(),bindings,null);
    }

    private CookingProcessBinding binding(BindingInput input,Map<UUID,RecipeIngredient> ingredients){return binding(input,ingredients,Map.of());}
    private CookingProcessBinding binding(BindingInput input,Map<UUID,RecipeIngredient> ingredients,Map<UUID,PreparedComponent> components) {
        RecipeIngredient ingredient=input.recipeIngredientId()==null?null:ingredients.get(input.recipeIngredientId());
        if(input.recipeIngredientId()!=null&&ingredient==null)throw new InvalidInputException("Process binding references an ingredient outside the recipe");
        if(ingredient!=null&&input.productTemplateId()!=null&&!ingredient.productTemplate().id().equals(input.productTemplateId()))throw new InvalidInputException("Process ingredient and product template do not match");
        ProductTemplate product=ingredient!=null?ingredient.productTemplate():(input.productTemplateId()==null?null:templates.get(input.productTemplateId()));
        PreparedComponent component=input.preparedComponentId()==null?null:components.get(input.preparedComponentId());if(input.preparedComponentId()!=null&&component==null)throw new InvalidInputException("Process binding references a component outside the recipe");
        return new CookingProcessBinding(null,input.parameterKey(),input.recipeIngredientId(),product,new CookingProcessValue(input.quantity(),input.unit(),input.durationSeconds(),input.temperatureCelsius(),input.heatLevel(),input.number(),blankToNull(input.text())),input.preparedComponentId(),component);
    }

    private CookingProcessBinding copyBinding(CookingProcessBinding source,Map<UUID,RecipeIngredient> copiedIngredients,Map<UUID,PreparedComponent> copiedComponents) {
        RecipeIngredient ingredient=source.recipeIngredientId()==null?null:copiedIngredients.get(source.recipeIngredientId());
        if(source.recipeIngredientId()!=null&&ingredient==null)throw new InvalidInputException("Recipe template process binding references an unknown ingredient");
        PreparedComponent component=source.preparedComponentId()==null?null:copiedComponents.get(source.preparedComponentId());
        return new CookingProcessBinding(null,source.parameterKey(),ingredient==null?null:ingredient.id(),ingredient==null?source.productTemplate():ingredient.productTemplate(),source.value(),component==null?null:component.id(),component);
    }

    private void validateAllocations(List<RecipeIngredient> ingredients,List<RecipeStep> steps){validateAllocations(ingredients,steps,List.of());}
    private void validateAllocations(List<RecipeIngredient> ingredients,List<RecipeStep> steps,List<PreparedComponent> components) {
        Map<UUID,RecipeIngredient> byId=ingredients.stream().collect(Collectors.toMap(RecipeIngredient::id,Function.identity()));
        Map<UUID,BigDecimal> allocated=new HashMap<>();
        for(PreparedComponent component:components)for(PreparedComponentIngredient allocation:component.ingredients()){RecipeIngredient ingredient=byId.get(allocation.recipeIngredientId());if(ingredient==null)throw new InvalidInputException("Prepared component references an ingredient outside the recipe");if(!dimension(allocation.unit()).equals(dimension(ingredient.unit())))throw new InvalidInputException("Prepared component allocation unit is incompatible: "+ingredient.productTemplate().name());allocated.merge(ingredient.id(),toBase(allocation.quantity(),allocation.unit()),BigDecimal::add);}
        for(RecipeStep step:steps) for(CookingProcessBinding binding:step.parameterBindings()) if(binding.recipeIngredientId()!=null&&binding.preparedComponentId()==null) {
            RecipeIngredient ingredient=byId.get(binding.recipeIngredientId());
            if(ingredient==null)throw new InvalidInputException("Process binding references an ingredient outside the recipe");
            BigDecimal amount=toBase(binding.value().quantity(),binding.value().unit());
            if(!dimension(binding.value().unit()).equals(dimension(ingredient.unit())))throw new InvalidInputException("Process allocation unit is not compatible with recipe ingredient: "+ingredient.productTemplate().name());
            allocated.merge(ingredient.id(),amount,BigDecimal::add);
        }
        for(RecipeIngredient ingredient:ingredients) if(allocated.getOrDefault(ingredient.id(),BigDecimal.ZERO).compareTo(toBase(ingredient.quantity(),ingredient.unit()))>0)
            throw new InvalidInputException("Process allocations exceed recipe ingredient quantity: "+ingredient.productTemplate().name());
    }

    private String dimension(RecipeUnit unit){if(unit==null)throw new InvalidInputException("Process ingredient unit is required");return switch(unit){case GRAM->"MASS";case PIECE->"COUNT";case MILLILITER,TEASPOON,TABLESPOON,DECILITER->"VOLUME";case GRINDER_TURN->"GRINDER_TURN";};}
    private BigDecimal toBase(BigDecimal value,RecipeUnit unit){if(value==null||value.signum()<=0)throw new InvalidInputException("Process ingredient quantity must be positive");BigDecimal factor=switch(unit){case GRAM,MILLILITER,PIECE,GRINDER_TURN->BigDecimal.ONE;case TEASPOON->new BigDecimal("5");case TABLESPOON->new BigDecimal("15");case DECILITER->new BigDecimal("100");};return value.multiply(factor);}
    private Recipe render(Recipe recipe){return render(recipe,BigDecimal.ONE);}
    private Recipe render(Recipe recipe,BigDecimal portions){List<RecipeStep> rendered=recipe.steps().stream().map(s->s.type()==RecipeStepType.PROCESS?new RecipeStep(s.id(),s.type(),null,s.sortOrder(),s.cookingProcessId(),s.parameterBindings(),processes.render(s.cookingProcessId(),scaledBindings(s.parameterBindings(),portions))):s).toList();List<String> equipment=processes.equipmentOverview(recipe.steps().stream().filter(s->s.type()==RecipeStepType.PROCESS).map(RecipeStep::cookingProcessId).toList(),recipe.equipmentRequirements());List<RecipePreparationStep> preparation=aggregatePreparation(recipe.preparationSteps(),rendered);List<PreparedComponent> components=recipe.preparedComponents().stream().map(c->scaleComponent(c,portions)).toList();return new Recipe(recipe.id(),recipe.userId(),recipe.sourceTemplateId(),recipe.name(),recipe.description(),recipe.createdAt(),recipe.updatedAt(),recipe.ingredients(),rendered,preparation,recipe.equipmentRequirements(),equipment,components);}
    private List<RecipePreparationStep> aggregatePreparation(List<RecipePreparationStep> explicit,List<RecipeStep> steps){LinkedHashMap<String,RecipePreparationStep> result=new LinkedHashMap<>();for(RecipePreparationStep value:explicit)result.putIfAbsent(preparationKey(value.instruction()),value);int order=explicit.size()+1;for(RecipeStep step:steps)if(step.renderedProcess()!=null)for(String instruction:step.renderedProcess().preparationInstructions()){String key=preparationKey(instruction);result.putIfAbsent(key,new RecipePreparationStep(UUID.nameUUIDFromBytes(("process-preparation:"+key).getBytes(java.nio.charset.StandardCharsets.UTF_8)),instruction,order++));}int sorted=1;List<RecipePreparationStep> output=new ArrayList<>();for(RecipePreparationStep value:result.values())output.add(new RecipePreparationStep(value.id(),value.instruction(),sorted++));return List.copyOf(output);}
    private String preparationKey(String value){return value.toLowerCase(Locale.forLanguageTag("da")).replaceAll("[^a-zæøå0-9]+"," ").trim();}
    private List<CookingProcessBinding> scaledBindings(List<CookingProcessBinding> bindings,BigDecimal portions){return bindings.stream().map(binding->{CookingProcessValue value=binding.value();CookingProcessValue scaled=value==null||value.quantity()==null?value:new CookingProcessValue(value.quantity().multiply(portions),value.unit(),value.durationSeconds(),value.temperatureCelsius(),value.heatLevel(),value.number(),value.text());PreparedComponent component=binding.preparedComponent()==null?null:scaleComponent(binding.preparedComponent(),portions);return new CookingProcessBinding(binding.id(),binding.parameterKey(),binding.recipeIngredientId(),binding.productTemplate(),scaled,binding.preparedComponentId(),component);}).toList();}
    private PreparedComponent scaleComponent(PreparedComponent component,BigDecimal portions){return new PreparedComponent(component.id(),component.key(),component.name(),component.sortOrder(),component.ingredients().stream().map(value->new PreparedComponentIngredient(value.id(),value.recipeIngredientId(),value.productTemplate(),value.quantity().multiply(portions),value.unit(),value.sortOrder())).toList(),component.preparationSteps());}
    private void validateOrders(List<Integer> values){if(values.stream().anyMatch(v->v==null||v<=0)||new HashSet<>(values).size()!=values.size())throw new InvalidInputException("Sort order must be positive and unique");}
    private String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
}
