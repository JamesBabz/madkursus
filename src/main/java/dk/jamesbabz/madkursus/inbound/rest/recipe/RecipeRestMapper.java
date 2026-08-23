package dk.jamesbabz.madkursus.inbound.rest.recipe;

import java.time.ZoneOffset;

import dk.jamesbabz.madkursus.inbound.rest.dto.RecipeDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.RecipeIngredientDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.RecipeStepDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.RecipeUnitDTO;
import dk.jamesbabz.madkursus.inbound.rest.producttemplate.ProductTemplateRestMapper;
import dk.jamesbabz.madkursus.service.models.Recipe;
import dk.jamesbabz.madkursus.service.models.RecipeRequirement;
import dk.jamesbabz.madkursus.service.models.PreparedComponent;
import dk.jamesbabz.madkursus.service.models.CookingProcessValue;
import dk.jamesbabz.madkursus.inbound.rest.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecipeRestMapper {
    private final ProductTemplateRestMapper productTemplateMapper;

    public RecipeDTO toDto(Recipe recipe) {
        var ingredients = recipe.ingredients().stream().map(ingredient -> new RecipeIngredientDTO(
                ingredient.id(), ingredient.productTemplate().id(),
                productTemplateMapper.toDto(ingredient.productTemplate()), ingredient.quantity(),
                RecipeUnitDTO.valueOf(ingredient.unit().name()), ingredient.sortOrder())
                .preparation(ingredient.preparation())).toList();
        var steps = recipe.steps().stream()
                .map(step -> new RecipeStepDTO(step.id(), RecipeStepTypeDTO.valueOf(step.type().name()),
                        step.sortOrder(), step.parameterBindings().stream().map(this::binding).toList())
                        .instruction(step.instruction()).cookingProcessId(step.cookingProcessId())
                        .renderedProcess(step.renderedProcess() == null ? null : new RenderedCookingProcessDTO(
                                step.renderedProcess().instructions(), step.renderedProcess().completionCriterion(),
                                step.renderedProcess().warnings()).processName(step.renderedProcess().processName())
                                .activeDurationSeconds(step.renderedProcess().activeDurationSeconds()).passiveDurationSeconds(step.renderedProcess().passiveDurationSeconds())
                                .durationSummary(step.renderedProcess().durationSummary()).preparationInstructions(step.renderedProcess().preparationInstructions()).inputSummary(step.renderedProcess().inputSummary())))
                .toList();
        var preparation=recipe.preparationSteps().stream().map(value->new RecipePreparationStepDTO(value.instruction(),value.sortOrder()).id(value.id())).toList();
        return new RecipeDTO(recipe.id(), recipe.name(), ingredients, steps,preparation,recipe.equipmentOverview(),
                recipe.createdAt().atOffset(ZoneOffset.UTC), recipe.updatedAt().atOffset(ZoneOffset.UTC))
                .description(recipe.description()).sourceTemplateId(recipe.sourceTemplateId()).preparedComponents(recipe.preparedComponents().stream().map(this::component).toList());
    }

    private CookingProcessBindingDTO binding(dk.jamesbabz.madkursus.service.models.CookingProcessBinding binding) {
        var value = binding.value()==null?new CookingProcessValue(null,null,null,null,null,null,null):binding.value();
        return new CookingProcessBindingDTO(binding.parameterKey()).id(binding.id()).recipeIngredientId(binding.recipeIngredientId())
                .preparedComponentId(binding.preparedComponentId())
                .productTemplateId(binding.productTemplate() == null ? null : binding.productTemplate().id())
                .productTemplate(binding.productTemplate() == null ? null : productTemplateMapper.toDto(binding.productTemplate()))
                .quantity(value.quantity()).unit(value.unit() == null ? null : RecipeUnitDTO.valueOf(value.unit().name()))
                .durationSeconds(value.durationSeconds()).temperatureCelsius(value.temperatureCelsius())
                .heatLevel(value.heatLevel() == null ? null : HeatLevelDTO.valueOf(value.heatLevel().name()))
                .number(value.number()).text(value.text());

    }
    private PreparedComponentDTO component(PreparedComponent value){return new PreparedComponentDTO(value.key(),value.name(),value.sortOrder(),value.ingredients().stream().map(a->new PreparedComponentIngredientDTO(a.recipeIngredientId(),a.quantity(),RecipeUnitDTO.valueOf(a.unit().name()),a.sortOrder()).id(a.id()).productTemplate(productTemplateMapper.toDto(a.productTemplate()))).toList(),value.preparationSteps().stream().map(p->new RecipePreparationStepDTO(p.instruction(),p.sortOrder()).id(p.id())).toList()).id(value.id());}

    public RecipeRequirementDTO toDto(RecipeRequirement requirement) {
        return new RecipeRequirementDTO(productTemplateMapper.toDto(requirement.productTemplate()),
                InventoryTrackingModeDTO.valueOf(requirement.trackingMode().name()),
                UnitDTO.valueOf(requirement.unit().name()), requirement.satisfied())
                .productId(requirement.product() == null ? null : requirement.product().id())
                .requiredQuantity(requirement.requiredQuantity()).physicalQuantity(requirement.physicalQuantity())
                .reservedQuantity(requirement.reservedQuantity()).availableQuantity(requirement.availableQuantity())
                .plannedShortfall(requirement.plannedShortfall()).missingQuantity(requirement.missingQuantity())
                .plannedUsageCount(requirement.plannedUsageCount()).reservations(requirement.reservations().stream()
                        .map(value->new InventoryReservationDetailDTO(value.mealPlanId(),value.mealPlanName(),value.plannedRecipeId(),
                                value.recipeId(),value.recipeName(),value.portions(),UnitDTO.valueOf(value.unit().name()))
                                .reservedQuantity(value.reservedQuantity())).toList()).warning(requirement.warning());
    }
    public RecipeRequirementCalculationDTO toDto(dk.jamesbabz.madkursus.service.models.RecipeRequirementCalculation calculation) { return new RecipeRequirementCalculationDTO(calculation.requirements().stream().map(this::toDto).toList()); }
    public CookRecipeResultDTO toDto(dk.jamesbabz.madkursus.service.models.RecipeCookResult result) { return new CookRecipeResultDTO(toDto(result.recipe()),result.portions(),result.history().cookedAt().atOffset(ZoneOffset.UTC),result.deductions().stream().map(this::toDto).toList(),result.warnings()); }
}
