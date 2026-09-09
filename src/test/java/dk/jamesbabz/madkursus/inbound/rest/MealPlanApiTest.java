package dk.jamesbabz.madkursus.inbound.rest;

import dk.jamesbabz.madkursus.inbound.rest.mealplan.*;
import dk.jamesbabz.madkursus.inbound.rest.producttemplate.ProductTemplateRestMapper;
import dk.jamesbabz.madkursus.inbound.rest.recipe.RecipeRestMapper;
import dk.jamesbabz.madkursus.service.applications.*;
import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.ports.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class MealPlanApiTest {
    private final MealPlanPort port = mock(MealPlanPort.class);
    private final RecipeService recipes = mock(RecipeService.class);
    private final CurrentUserProvider user = mock(CurrentUserProvider.class);
    private final Recipe recipe = new Recipe(UUID.randomUUID(), UUID.randomUUID(), "Dinner", null,
            Instant.now(), Instant.now(), List.of(), List.of());
    private final MealPlan plan = new MealPlan(UUID.randomUUID(), recipe.userId(), "Week", Instant.now(), Instant.now(),
            List.of(new PlannedRecipe(UUID.randomUUID(), recipe, 2, 1, PlannedRecipeStatus.PLANNED)));

    private MockMvc mvc() {
        when(user.currentUserId()).thenReturn(recipe.userId());
        var service = new MealPlanService(port, recipes, mock(RecipeInteractionService.class), user);
        var mapper = new RecipeRestMapper(mock(ProductTemplateRestMapper.class));
        return standaloneSetup(new MealPlanApiController(new MealPlanApiDelegateImpl(service,
                new MealPlanRestMapper(mapper), mapper))).build();
    }

    @Test void getsMealPlansWithUnavailableCarbohydrates() throws Exception {
        var mvc = mvc();
        when(port.findAllByUserId(recipe.userId())).thenReturn(List.of(plan));
        when(port.findByIdAndUserId(plan.id(), recipe.userId())).thenReturn(Optional.of(plan));
        mvc.perform(get("/v1/meal-plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recipes[0].recipe.id").value(recipe.id().toString()))
                .andExpect(jsonPath("$[0].recipes[0].recipe.carbohydrates").value(nullValue()));
        mvc.perform(get("/v1/meal-plans/{id}", plan.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes[0].recipe.carbohydrates").value(nullValue()));
    }

    @Test void createsMealPlanAndReturnsRecipeWithoutCarbohydrates() throws Exception {
        var mvc = mvc();
        when(recipes.get(recipe.id())).thenReturn(recipe);
        when(port.save(any())).thenAnswer(call -> {
            MealPlan saved = call.getArgument(0);
            return new MealPlan(plan.id(), saved.userId(), saved.name(), saved.createdAt(), saved.updatedAt(), saved.recipes());
        });
        mvc.perform(post("/v1/meal-plans").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Week","recipes":[{"recipeId":"%s","portions":2}]}
                """.formatted(recipe.id())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/meal-plans/" + plan.id()))
                .andExpect(jsonPath("$.recipes[0].recipe.id").value(recipe.id().toString()))
                .andExpect(jsonPath("$.recipes[0].recipe.carbohydrates").value(nullValue()));
        verify(port).save(any());
    }
}
