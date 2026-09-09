package dk.jamesbabz.madkursus.inbound.rest.mealplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import dk.jamesbabz.madkursus.inbound.rest.recipe.RecipeRestMapper;
import dk.jamesbabz.madkursus.service.models.*;
import org.junit.jupiter.api.Test;

class MealPlanRestMapperTest {
    @Test void mapsRecipeWithoutCarbohydrateEnrichment() {
        Recipe recipe = new Recipe(UUID.randomUUID(), UUID.randomUUID(), "Dinner", null,
                Instant.now(), Instant.now(), List.of(), List.of());
        MealPlan plan = new MealPlan(UUID.randomUUID(), recipe.userId(), "Week", Instant.now(), Instant.now(),
                List.of(new PlannedRecipe(UUID.randomUUID(), recipe, 2, 1, PlannedRecipeStatus.PLANNED)));
        var mapper = new RecipeRestMapper(mock(dk.jamesbabz.madkursus.inbound.rest.producttemplate.ProductTemplateRestMapper.class));

        var dto = new MealPlanRestMapper(mapper).toDto(plan);

        assertThat(dto.getRecipes()).hasSize(1);
        assertThat(dto.getRecipes().getFirst().getRecipe().getId()).isEqualTo(recipe.id());
        assertThat(dto.getRecipes().getFirst().getRecipe().getCarbohydrates()).isNull();
    }

    @Test void historicalOccurrenceKeepsMeaningfulDisplayAfterRecipeDeletion() {
        PlannedRecipe historical=new PlannedRecipe(UUID.randomUUID(),null,"Frikadeller",4,1,PlannedRecipeStatus.COOKED);
        MealPlan plan=new MealPlan(UUID.randomUUID(),UUID.randomUUID(),"Uge 33",Instant.now(),Instant.now(),List.of(historical));

        var dto=new MealPlanRestMapper(mock(RecipeRestMapper.class)).toDto(plan).getRecipes().getFirst();

        assertThat(dto.getRecipe()).isNull(); assertThat(dto.getRecipeName()).isEqualTo("Frikadeller");
        assertThat(dto.getStatus().name()).isEqualTo("COOKED");
    }
}
