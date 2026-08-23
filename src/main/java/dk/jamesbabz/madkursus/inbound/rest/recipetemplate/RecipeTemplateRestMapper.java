package dk.jamesbabz.madkursus.inbound.rest.recipetemplate;

import java.time.ZoneOffset;

import dk.jamesbabz.madkursus.inbound.rest.dto.*;
import dk.jamesbabz.madkursus.inbound.rest.producttemplate.ProductTemplateRestMapper;
import dk.jamesbabz.madkursus.service.applications.RecipeTemplateService;
import dk.jamesbabz.madkursus.service.models.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecipeTemplateRestMapper {
    private final ProductTemplateRestMapper products;
    private final RecipeTemplateService service;

    public RecipeTemplateSummaryDTO summary(RecipeTemplate template) {
        var copied = service.copiedRecipe(template.id());
        return new RecipeTemplateSummaryDTO(template.id(), template.name(), template.active(), copied.isPresent())
                .description(template.description()).userRecipeId(copied.map(Recipe::id).orElse(null));
    }

    public RecipeTemplateDTO detail(RecipeTemplate template) {
        var copied = service.copiedRecipe(template.id());
        var ingredients = template.ingredients().stream().map(ingredient -> new RecipeTemplateIngredientDTO(
                ingredient.id(), products.toDto(ingredient.productTemplate()), ingredient.quantity(),
                RecipeUnitDTO.valueOf(ingredient.unit().name()), ingredient.sortOrder())
                .preparation(ingredient.preparation())).toList();
        var steps = template.steps().stream().map(step -> new RecipeTemplateStepDTO(step.id(),
                RecipeStepTypeDTO.valueOf(step.type().name()), step.sortOrder(),
                step.parameterBindings().stream().map(this::binding).toList())
                .instruction(step.instruction()).cookingProcessId(step.cookingProcessId())
                .renderedProcess(step.renderedProcess() == null ? null : new RenderedCookingProcessDTO(
                        step.renderedProcess().instructions(), step.renderedProcess().completionCriterion(),
                        step.renderedProcess().warnings()).processName(step.renderedProcess().processName())
                        .activeDurationSeconds(step.renderedProcess().activeDurationSeconds()).passiveDurationSeconds(step.renderedProcess().passiveDurationSeconds())
                        .durationSummary(step.renderedProcess().durationSummary()).preparationInstructions(step.renderedProcess().preparationInstructions()).inputSummary(step.renderedProcess().inputSummary()))).toList();
        var preparation=template.preparationSteps().stream().map(value->new RecipePreparationStepDTO(value.instruction(),value.sortOrder()).id(value.id())).toList();
        return new RecipeTemplateDTO(template.id(), template.name(), template.active(), copied.isPresent(),
                template.createdAt().atOffset(ZoneOffset.UTC), template.updatedAt().atOffset(ZoneOffset.UTC),
                ingredients, steps,preparation,template.equipmentOverview()).description(template.description())
                .userRecipeId(copied.map(Recipe::id).orElse(null)).preparedComponents(template.preparedComponents().stream().map(this::component).toList());
    }

    private CookingProcessBindingDTO binding(CookingProcessBinding binding) {
        var value = binding.value()==null?new CookingProcessValue(null,null,null,null,null,null,null):binding.value();
        return new CookingProcessBindingDTO(binding.parameterKey()).id(binding.id()).recipeIngredientId(binding.recipeIngredientId()).preparedComponentId(binding.preparedComponentId())
                .productTemplateId(binding.productTemplate() == null ? null : binding.productTemplate().id())
                .productTemplate(binding.productTemplate() == null ? null : products.toDto(binding.productTemplate()))
                .quantity(value.quantity()).unit(value.unit() == null ? null : RecipeUnitDTO.valueOf(value.unit().name()))
                .durationSeconds(value.durationSeconds()).temperatureCelsius(value.temperatureCelsius())
                .heatLevel(value.heatLevel() == null ? null : HeatLevelDTO.valueOf(value.heatLevel().name()))
                .number(value.number()).text(value.text());
    }
    private PreparedComponentDTO component(PreparedComponent value){return new PreparedComponentDTO(value.key(),value.name(),value.sortOrder(),value.ingredients().stream().map(a->new PreparedComponentIngredientDTO(a.recipeIngredientId(),a.quantity(),RecipeUnitDTO.valueOf(a.unit().name()),a.sortOrder()).id(a.id()).productTemplate(products.toDto(a.productTemplate()))).toList(),value.preparationSteps().stream().map(p->new RecipePreparationStepDTO(p.instruction(),p.sortOrder()).id(p.id())).toList()).id(value.id());}
}
