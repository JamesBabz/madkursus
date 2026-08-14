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
                        step.renderedProcess().warnings()))).toList();
        return new RecipeTemplateDTO(template.id(), template.name(), template.active(), copied.isPresent(),
                template.createdAt().atOffset(ZoneOffset.UTC), template.updatedAt().atOffset(ZoneOffset.UTC),
                ingredients, steps).description(template.description())
                .userRecipeId(copied.map(Recipe::id).orElse(null));
    }

    private CookingProcessBindingDTO binding(CookingProcessBinding binding) {
        var value = binding.value();
        return new CookingProcessBindingDTO(binding.parameterKey()).id(binding.id()).recipeIngredientId(binding.recipeIngredientId())
                .productTemplateId(binding.productTemplate() == null ? null : binding.productTemplate().id())
                .productTemplate(binding.productTemplate() == null ? null : products.toDto(binding.productTemplate()))
                .quantity(value.quantity()).unit(value.unit() == null ? null : RecipeUnitDTO.valueOf(value.unit().name()))
                .durationSeconds(value.durationSeconds()).temperatureCelsius(value.temperatureCelsius())
                .heatLevel(value.heatLevel() == null ? null : HeatLevelDTO.valueOf(value.heatLevel().name()))
                .number(value.number()).text(value.text());
    }
}
