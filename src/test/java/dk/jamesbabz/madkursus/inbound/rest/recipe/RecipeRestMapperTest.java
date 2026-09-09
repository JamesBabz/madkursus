package dk.jamesbabz.madkursus.inbound.rest.recipe;

import dk.jamesbabz.madkursus.inbound.rest.producttemplate.ProductTemplateRestMapper;
import dk.jamesbabz.madkursus.service.models.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RecipeRestMapperTest {
    private final RecipeRestMapper mapper = new RecipeRestMapper(mock(ProductTemplateRestMapper.class));

    @Test void mapsAvailableCarbohydratesAndUnknownIngredient() {
        var known = new CarbohydrateIngredientResult(UUID.randomUUID(), UUID.randomUUID(), "Rice", new BigDecimal("24"), true);
        var unknown = new CarbohydrateIngredientResult(UUID.randomUUID(), UUID.randomUUID(), "Sauce", null, false);
        var result = new CarbohydrateResult(new BigDecimal("12"), new BigDecimal("24"), false, 1, List.of(known, unknown));

        var dto = mapper.toDto(recipe(result)).getCarbohydrates();

        assertThat(dto.getPerPortionGrams()).isEqualByComparingTo("12");
        assertThat(dto.getTotalGrams()).isEqualByComparingTo("24");
        assertThat(dto.getComplete()).isFalse();
        assertThat(dto.getUnknownIngredientCount()).isOne();
        assertThat(dto.getIngredients().getFirst().getRecipeIngredientId()).isEqualTo(known.recipeIngredientId());
        assertThat(dto.getIngredients().getFirst().getGrams()).isEqualByComparingTo("24");
        assertThat(dto.getIngredients().getFirst().getKnown()).isTrue();
        assertThat(dto.getIngredients().getLast().getKnown()).isFalse();
        assertThat(dto.getIngredients().getLast().getGrams()).isNull();
    }

    @Test void unavailableCarbohydratesRemainNullInsteadOfZero() {
        var recipe = recipe(null);
        var dto = mapper.toDto(recipe);
        assertThat(dto.getId()).isEqualTo(recipe.id());
        assertThat(dto.getName()).isEqualTo(recipe.name());
        assertThat(dto.getCarbohydrates()).isNull();
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(dto)).isEmpty();
        }
    }

    @Test void knownZeroRemainsAnAvailableResult() {
        var dto = mapper.toDto(recipe(new CarbohydrateResult(BigDecimal.ZERO, BigDecimal.ZERO, true, 0, List.of())))
                .getCarbohydrates();
        assertThat(dto).isNotNull();
        assertThat(dto.getPerPortionGrams()).isZero();
        assertThat(dto.getTotalGrams()).isZero();
        assertThat(dto.getComplete()).isTrue();
    }

    private Recipe recipe(CarbohydrateResult result) {
        return new Recipe(UUID.randomUUID(), UUID.randomUUID(), null, "Dinner", null, Instant.now(), Instant.now(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), result);
    }
}
