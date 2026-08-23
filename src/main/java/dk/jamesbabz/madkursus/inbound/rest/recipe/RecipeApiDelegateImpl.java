package dk.jamesbabz.madkursus.inbound.rest.recipe;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import dk.jamesbabz.madkursus.inbound.rest.RecipeApiDelegate;
import dk.jamesbabz.madkursus.inbound.rest.dto.RecipeDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.RecipeInputDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.*;
import dk.jamesbabz.madkursus.service.applications.RecipeService;
import dk.jamesbabz.madkursus.service.applications.RecipeInteractionService;
import dk.jamesbabz.madkursus.service.models.RecipeUnit;
import dk.jamesbabz.madkursus.service.models.RecipeSelection;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecipeApiDelegateImpl implements RecipeApiDelegate {
    private final RecipeService service;
    private final RecipeRestMapper mapper;
    private final RecipeInteractionService interactionService;

    @Override
    public ResponseEntity<List<RecipeDTO>> getRecipes() {
        return ResponseEntity.ok(service.getAll().stream().map(mapper::toDto).toList());
    }

    @Override
    public ResponseEntity<RecipeDTO> getRecipe(UUID id, java.math.BigDecimal portions) {
        return ResponseEntity.ok(mapper.toDto(service.get(id, portions == null ? java.math.BigDecimal.ONE : portions)));
    }

    @Override
    public ResponseEntity<RecipeDTO> createRecipe(RecipeInputDTO request) {
        RecipeDTO result = mapper.toDto(service.create(request.getName(), request.getDescription(),
                ingredients(request), steps(request),preparation(request),equipment(request),components(request)));
        return ResponseEntity.created(URI.create("/v1/recipes/" + result.getId())).body(result);
    }

    @Override
    public ResponseEntity<RecipeDTO> updateRecipe(UUID id, RecipeInputDTO request) {
        return ResponseEntity.ok(mapper.toDto(service.update(id, request.getName(), request.getDescription(),
                ingredients(request), steps(request),preparation(request),equipment(request),components(request))));
    }

    @Override
    public ResponseEntity<Void> deleteRecipe(UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override public ResponseEntity<RecipeRequirementCalculationDTO> calculateRecipeRequirements(RecipeSelectionsDTO request) { return ResponseEntity.ok(calculation(interactionService.calculate(selections(request)))); }
    @Override public ResponseEntity<RecipeRequirementCalculationDTO> addMissingRecipeRequirementsToShoppingList(RecipeSelectionsDTO request) { return ResponseEntity.ok(calculation(interactionService.addMissingToShoppingList(selections(request)))); }
    @Override public ResponseEntity<CookRecipeResultDTO> cookRecipe(UUID id, CookRecipeRequestDTO request) { return ResponseEntity.ok(mapper.toDto(interactionService.cook(id,request.getPortions()))); }
    private List<RecipeSelection> selections(RecipeSelectionsDTO request){return request.getRecipes().stream().map(s->new RecipeSelection(s.getRecipeId(),s.getPortions())).toList();}
    private RecipeRequirementCalculationDTO calculation(dk.jamesbabz.madkursus.service.models.RecipeRequirementCalculation c){return mapper.toDto(c);}

    private List<RecipeService.IngredientInput> ingredients(RecipeInputDTO request) {
        return request.getIngredients().stream().map(i -> new RecipeService.IngredientInput(
                i.getId(), i.getProductTemplateId(), i.getQuantity(), RecipeUnit.valueOf(i.getUnit().name()),
                i.getPreparation(), i.getSortOrder())).toList();
    }

    private List<RecipeService.StepInput> steps(RecipeInputDTO request) {
        return request.getSteps().stream()
                .map(s -> new RecipeService.StepInput(
                        s.getType() == null ? dk.jamesbabz.madkursus.service.models.RecipeStepType.TEXT : dk.jamesbabz.madkursus.service.models.RecipeStepType.valueOf(s.getType().name()),
                        s.getInstruction(), s.getSortOrder(), s.getCookingProcessId(),
                        s.getParameterBindings().stream().map(b -> new RecipeService.BindingInput(
                                b.getParameterKey(), b.getRecipeIngredientId(), b.getProductTemplateId(), b.getQuantity(),
                                b.getUnit() == null ? null : RecipeUnit.valueOf(b.getUnit().name()),
                                b.getDurationSeconds(), b.getTemperatureCelsius(),
                                b.getHeatLevel() == null ? null : dk.jamesbabz.madkursus.service.models.HeatLevel.valueOf(b.getHeatLevel().name()),
                                b.getNumber(), b.getText(),b.getPreparedComponentId())).toList())).toList();
    }
    private List<RecipeService.PreparationInput> preparation(RecipeInputDTO request){return request.getPreparationSteps()==null?List.of():request.getPreparationSteps().stream().map(value->new RecipeService.PreparationInput(value.getId(),value.getInstruction(),value.getSortOrder())).toList();}
    private List<RecipeService.EquipmentInput> equipment(RecipeInputDTO request){return request.getEquipmentRequirements()==null?List.of():request.getEquipmentRequirements().stream().map(value->new RecipeService.EquipmentInput(value.getId(),value.getEquipmentType()==null?null:dk.jamesbabz.madkursus.service.models.EquipmentType.valueOf(value.getEquipmentType().name()),value.getLabel(),value.getSortOrder())).toList();}
    private List<RecipeService.ComponentInput> components(RecipeInputDTO request){return request.getPreparedComponents()==null?List.of():request.getPreparedComponents().stream().map(value->new RecipeService.ComponentInput(value.getId(),value.getKey(),value.getName(),value.getSortOrder(),value.getIngredients().stream().map(a->new RecipeService.ComponentIngredientInput(a.getId(),a.getRecipeIngredientId(),a.getQuantity(),RecipeUnit.valueOf(a.getUnit().name()),a.getSortOrder())).toList(),value.getPreparationSteps().stream().map(p->new RecipeService.PreparationInput(p.getId(),p.getInstruction(),p.getSortOrder())).toList())).toList();}
}
