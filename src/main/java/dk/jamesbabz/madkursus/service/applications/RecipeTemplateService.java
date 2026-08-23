package dk.jamesbabz.madkursus.service.applications;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dk.jamesbabz.madkursus.service.exceptions.ConflictException;
import dk.jamesbabz.madkursus.service.exceptions.InvalidInputException;
import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import dk.jamesbabz.madkursus.service.models.CookingProcessBinding;
import dk.jamesbabz.madkursus.service.models.CookingProcessValue;
import dk.jamesbabz.madkursus.service.models.PreparedComponent;
import dk.jamesbabz.madkursus.service.models.PreparedComponentIngredient;
import dk.jamesbabz.madkursus.service.models.Recipe;
import dk.jamesbabz.madkursus.service.models.RecipeStepType;
import dk.jamesbabz.madkursus.service.models.RecipeTemplate;
import dk.jamesbabz.madkursus.service.models.RecipeTemplateStep;
import dk.jamesbabz.madkursus.service.ports.CurrentUserProvider;
import dk.jamesbabz.madkursus.service.ports.RecipePort;
import dk.jamesbabz.madkursus.service.ports.RecipeTemplatePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecipeTemplateService {
    private final RecipeTemplatePort port;
    private final RecipePort recipes;
    private final RecipeService recipeService;
    private final CurrentUserProvider currentUser;
    private final CookingProcessService cookingProcesses;

    public List<RecipeTemplate> search(String query) {
        return port.search(query);
    }

    /** Returns the canonical one-portion template used by the copy flow. */
    public RecipeTemplate get(UUID id) {
        return port.findById(id).orElseThrow(() -> new ResourceNotFoundException("Recipe template", id));
    }

    /**
     * Returns a read projection with PROCESS instructions rendered for the requested
     * preview portions. Only ingredient quantities are scaled; duration, heat and
     * temperature remain process values.
     */
    public RecipeTemplate getRendered(UUID id, int portions) {
        if (portions <= 0) throw new InvalidInputException("Portions must be positive");
        RecipeTemplate template = get(id);
        BigDecimal factor = BigDecimal.valueOf(portions);
        List<RecipeTemplateStep> steps = template.steps().stream().map(step -> render(step, factor)).toList();
        List<String> equipment=cookingProcesses.equipmentOverview(template.steps().stream().filter(step->step.type()==RecipeStepType.PROCESS).map(RecipeTemplateStep::cookingProcessId).toList(),template.equipmentRequirements());
        List<dk.jamesbabz.madkursus.service.models.RecipePreparationStep> preparation=aggregatePreparation(template.preparationSteps(),steps);
        return new RecipeTemplate(template.id(), template.name(), template.description(), template.active(),
                template.createdAt(), template.updatedAt(), template.ingredients(), steps,preparation,template.equipmentRequirements(),equipment,template.preparedComponents().stream().map(c->scaleComponent(c,factor)).toList());
    }

    public Optional<Recipe> copiedRecipe(UUID templateId) {
        return recipes.findByUserIdAndSourceTemplateId(currentUser.currentUserId(), templateId);
    }

    @Transactional
    public Recipe copy(UUID id) {
        RecipeTemplate template = get(id);
        if (copiedRecipe(id).isPresent()) throw new ConflictException("Recipe template is already added");
        return recipeService.createFromTemplate(template);
    }

    private RecipeTemplateStep render(RecipeTemplateStep step, BigDecimal portions) {
        if (step.type() != RecipeStepType.PROCESS) return step;
        List<CookingProcessBinding> scaled = step.parameterBindings().stream()
                .map(binding -> scaleIngredientBinding(binding, portions)).toList();
        return new RecipeTemplateStep(step.id(), step.type(), step.instruction(), step.sortOrder(),
                step.cookingProcessId(), step.parameterBindings(), cookingProcesses.render(step.cookingProcessId(), scaled));
    }

    private CookingProcessBinding scaleIngredientBinding(CookingProcessBinding binding, BigDecimal portions) {
        if (binding.preparedComponent()!=null)return new CookingProcessBinding(binding.id(),binding.parameterKey(),binding.recipeIngredientId(),binding.productTemplate(),binding.value(),binding.preparedComponentId(),scaleComponent(binding.preparedComponent(),portions));
        if (binding.value() == null || binding.value().quantity() == null) return binding;
        CookingProcessValue value = binding.value();
        PreparedComponent component=binding.preparedComponent()==null?null:scaleComponent(binding.preparedComponent(),portions);
        return new CookingProcessBinding(binding.id(), binding.parameterKey(), binding.recipeIngredientId(),
                binding.productTemplate(), new CookingProcessValue(value.quantity().multiply(portions), value.unit(),
                        value.durationSeconds(), value.temperatureCelsius(), value.heatLevel(), value.number(), value.text()),binding.preparedComponentId(),component);
    }
    private PreparedComponent scaleComponent(PreparedComponent c,BigDecimal factor){return new PreparedComponent(c.id(),c.key(),c.name(),c.sortOrder(),c.ingredients().stream().map(a->new PreparedComponentIngredient(a.id(),a.recipeIngredientId(),a.productTemplate(),a.quantity().multiply(factor),a.unit(),a.sortOrder())).toList(),c.preparationSteps());}
    private List<dk.jamesbabz.madkursus.service.models.RecipePreparationStep> aggregatePreparation(List<dk.jamesbabz.madkursus.service.models.RecipePreparationStep> explicit,List<RecipeTemplateStep> steps){java.util.LinkedHashMap<String,dk.jamesbabz.madkursus.service.models.RecipePreparationStep> result=new java.util.LinkedHashMap<>();for(var value:explicit)result.putIfAbsent(key(value.instruction()),value);int order=explicit.size()+1;for(var step:steps)if(step.renderedProcess()!=null)for(String instruction:step.renderedProcess().preparationInstructions()){String key=key(instruction);result.putIfAbsent(key,new dk.jamesbabz.madkursus.service.models.RecipePreparationStep(UUID.nameUUIDFromBytes(("process-preparation:"+key).getBytes(java.nio.charset.StandardCharsets.UTF_8)),instruction,order++));}int sorted=1;java.util.ArrayList<dk.jamesbabz.madkursus.service.models.RecipePreparationStep> output=new java.util.ArrayList<>();for(var value:result.values())output.add(new dk.jamesbabz.madkursus.service.models.RecipePreparationStep(value.id(),value.instruction(),sorted++));return List.copyOf(output);}
    private String key(String value){return value.toLowerCase(java.util.Locale.forLanguageTag("da")).replaceAll("[^a-zæøå0-9]+"," ").trim();}
}
