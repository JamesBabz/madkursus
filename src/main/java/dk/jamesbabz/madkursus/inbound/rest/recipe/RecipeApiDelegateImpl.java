package dk.jamesbabz.madkursus.inbound.rest.recipe;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import dk.jamesbabz.madkursus.inbound.rest.RecipeApiDelegate;
import dk.jamesbabz.madkursus.inbound.rest.dto.RecipeDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.RecipeInputDTO;
import dk.jamesbabz.madkursus.service.applications.RecipeService;
import dk.jamesbabz.madkursus.service.models.RecipeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecipeApiDelegateImpl implements RecipeApiDelegate {
    private final RecipeService service;
    private final RecipeRestMapper mapper;

    @Override
    public ResponseEntity<List<RecipeDTO>> getRecipes() {
        return ResponseEntity.ok(service.getAll().stream().map(mapper::toDto).toList());
    }

    @Override
    public ResponseEntity<RecipeDTO> getRecipe(UUID id) {
        return ResponseEntity.ok(mapper.toDto(service.get(id)));
    }

    @Override
    public ResponseEntity<RecipeDTO> createRecipe(RecipeInputDTO request) {
        RecipeDTO result = mapper.toDto(service.create(request.getName(), request.getDescription(),
                ingredients(request), steps(request)));
        return ResponseEntity.created(URI.create("/v1/recipes/" + result.getId())).body(result);
    }

    @Override
    public ResponseEntity<RecipeDTO> updateRecipe(UUID id, RecipeInputDTO request) {
        return ResponseEntity.ok(mapper.toDto(service.update(id, request.getName(), request.getDescription(),
                ingredients(request), steps(request))));
    }

    @Override
    public ResponseEntity<Void> deleteRecipe(UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private List<RecipeService.IngredientInput> ingredients(RecipeInputDTO request) {
        return request.getIngredients().stream().map(i -> new RecipeService.IngredientInput(
                i.getProductTemplateId(), i.getQuantity(), RecipeUnit.valueOf(i.getUnit().name()),
                i.getPreparation(), i.getSortOrder())).toList();
    }

    private List<RecipeService.StepInput> steps(RecipeInputDTO request) {
        return request.getSteps().stream()
                .map(s -> new RecipeService.StepInput(s.getInstruction(), s.getSortOrder())).toList();
    }
}
