package dk.jamesbabz.madkursus.inbound.rest.ai;

import dk.jamesbabz.madkursus.inbound.rest.AiApiDelegate;
import dk.jamesbabz.madkursus.inbound.rest.dto.AiChatRequestDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.AiChatModelDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.AiChatResponseDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.AiKnownRecipeDTO;
import dk.jamesbabz.madkursus.service.applications.AiChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiApiDelegateImpl implements AiApiDelegate {
    private final AiChatService service;

    @Override
    public ResponseEntity<AiChatModelDTO> getAiChatModel() {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new AiChatModelDTO(service.configuredModel()));
    }

    @Override
    public ResponseEntity<AiChatResponseDTO> chat(AiChatRequestDTO request) {
        var response = service.chat(request.getMessage(), request.getMaxAdditionalIngredients());
        var dto = new AiChatResponseDTO(response.answer());
        dto.setKnownRecipes(response.knownRecipes().stream().map(match -> {
            var recipe = new AiKnownRecipeDTO();
            recipe.setId(match.id()); recipe.setName(match.name());
            recipe.setSource(AiKnownRecipeDTO.SourceEnum.valueOf(match.source().name()));
            recipe.setState(AiKnownRecipeDTO.StateEnum.valueOf(match.state().name()));
            recipe.setMissingIngredientCount(match.missingIngredientCount());
            recipe.setMissingIngredients(match.missingIngredients().stream().map(i -> i.name()).toList());
            recipe.setUncertainIngredients(match.uncertainIngredients());
            return recipe;
        }).toList());
        return ResponseEntity.ok(dto);
    }
}
