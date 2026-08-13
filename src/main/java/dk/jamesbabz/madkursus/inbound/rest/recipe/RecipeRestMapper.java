package dk.jamesbabz.madkursus.inbound.rest.recipe;

import java.time.ZoneOffset;

import dk.jamesbabz.madkursus.inbound.rest.dto.RecipeDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.RecipeIngredientDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.RecipeStepDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.RecipeUnitDTO;
import dk.jamesbabz.madkursus.inbound.rest.producttemplate.ProductTemplateRestMapper;
import dk.jamesbabz.madkursus.service.models.Recipe;
import dk.jamesbabz.madkursus.service.models.RecipeRequirement;
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
                .map(step -> new RecipeStepDTO(step.id(), step.instruction(), step.sortOrder()))
                .toList();
        return new RecipeDTO(recipe.id(), recipe.name(), ingredients, steps,
                recipe.createdAt().atOffset(ZoneOffset.UTC), recipe.updatedAt().atOffset(ZoneOffset.UTC))
                .description(recipe.description()).sourceTemplateId(recipe.sourceTemplateId());
    }

    public RecipeRequirementDTO toDto(RecipeRequirement requirement) {
        return new RecipeRequirementDTO(productTemplateMapper.toDto(requirement.productTemplate()),
                InventoryTrackingModeDTO.valueOf(requirement.trackingMode().name()),
                UnitDTO.valueOf(requirement.unit().name()), requirement.satisfied())
                .productId(requirement.product() == null ? null : requirement.product().id())
                .requiredQuantity(requirement.requiredQuantity()).availableQuantity(requirement.availableQuantity())
                .missingQuantity(requirement.missingQuantity()).warning(requirement.warning());
    }
    public RecipeRequirementCalculationDTO toDto(dk.jamesbabz.madkursus.service.models.RecipeRequirementCalculation calculation) { return new RecipeRequirementCalculationDTO(calculation.requirements().stream().map(this::toDto).toList()); }
    public CookRecipeResultDTO toDto(dk.jamesbabz.madkursus.service.models.RecipeCookResult result) { return new CookRecipeResultDTO(toDto(result.recipe()),result.portions(),result.history().cookedAt().atOffset(ZoneOffset.UTC),result.deductions().stream().map(this::toDto).toList(),result.warnings()); }
}
