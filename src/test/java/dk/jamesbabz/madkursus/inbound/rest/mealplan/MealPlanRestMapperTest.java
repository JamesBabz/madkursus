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
    @Test void historicalOccurrenceKeepsMeaningfulDisplayAfterRecipeDeletion() {
        PlannedRecipe historical=new PlannedRecipe(UUID.randomUUID(),null,"Frikadeller",4,1,PlannedRecipeStatus.COOKED);
        MealPlan plan=new MealPlan(UUID.randomUUID(),UUID.randomUUID(),"Uge 33",Instant.now(),Instant.now(),List.of(historical));

        var dto=new MealPlanRestMapper(mock(RecipeRestMapper.class)).toDto(plan).getRecipes().getFirst();

        assertThat(dto.getRecipe()).isNull(); assertThat(dto.getRecipeName()).isEqualTo("Frikadeller");
        assertThat(dto.getStatus().name()).isEqualTo("COOKED");
    }
}
