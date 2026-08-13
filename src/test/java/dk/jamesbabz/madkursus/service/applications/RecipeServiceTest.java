package dk.jamesbabz.madkursus.service.applications;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import dk.jamesbabz.madkursus.service.exceptions.InvalidInputException;
import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import dk.jamesbabz.madkursus.service.models.ProductCategory;
import dk.jamesbabz.madkursus.service.models.ProductTemplate;
import dk.jamesbabz.madkursus.service.models.Recipe;
import dk.jamesbabz.madkursus.service.models.RecipeUnit;
import dk.jamesbabz.madkursus.service.models.Unit;
import dk.jamesbabz.madkursus.service.ports.CurrentUserProvider;
import dk.jamesbabz.madkursus.service.ports.RecipePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {
    @Mock RecipePort port;
    @Mock ProductTemplateService templates;
    @Mock CurrentUserProvider currentUser;
    @InjectMocks RecipeService service;

    @Test
    void createsOnePortionRecipeWithDecimalTemplateIngredientsAndOrderedSteps() {
        UUID userId = UUID.randomUUID(); UUID templateId = UUID.randomUUID();
        ProductTemplate onion = new ProductTemplate(templateId, "Løg", ProductCategory.VEGETABLE,
                Unit.PIECE, List.of(), false);
        when(currentUser.currentUserId()).thenReturn(userId);
        when(templates.get(templateId)).thenReturn(onion);
        when(port.save(any())).thenAnswer(call -> call.getArgument(0));

        Recipe result = service.create(" Kødsovs ", " Nem ",
                List.of(new RecipeService.IngredientInput(templateId, new BigDecimal("0.5"),
                        RecipeUnit.PIECE, " finthakket ", 1)),
                List.of(new RecipeService.StepInput(" Brun kødet. ", 2),
                        new RecipeService.StepInput(" Hak løget. ", 1)));

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.name()).isEqualTo("Kødsovs");
        assertThat(result.ingredients().getFirst().quantity()).isEqualByComparingTo("0.5");
        assertThat(result.ingredients().getFirst().productTemplate()).isEqualTo(onion);
        assertThat(result.steps()).extracting(step -> step.sortOrder()).containsExactly(1, 2);
    }

    @Test
    void scopesReadUpdateAndDeleteToCurrentUser() {
        UUID userId = UUID.randomUUID(); UUID recipeId = UUID.randomUUID();
        when(currentUser.currentUserId()).thenReturn(userId);
        when(port.findByIdAndUserId(recipeId, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(recipeId)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.update(recipeId, "Nope", null, List.of(), List.of()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(recipeId)).isInstanceOf(ResourceNotFoundException.class);
        verify(port, atLeastOnce()).findByIdAndUserId(recipeId, userId);
    }

    @Test
    void rejectsNonPositiveQuantityAndDuplicateSortOrder() {
        UUID templateId = UUID.randomUUID();
        assertThatThrownBy(() -> service.create("Test", null,
                List.of(new RecipeService.IngredientInput(templateId, BigDecimal.ZERO, RecipeUnit.GRAM, null, 1)), List.of()))
                .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> service.create("Test", null, List.of(),
                List.of(new RecipeService.StepInput("A", 1), new RecipeService.StepInput("B", 1))))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void updatePreservesRecipeIdentityAndReplacesAllEditableAggregateData() {
        UUID userId = UUID.randomUUID(); UUID recipeId = UUID.randomUUID();
        UUID onionId = UUID.randomUUID(); UUID saltId = UUID.randomUUID(); Instant createdAt = Instant.parse("2026-01-01T10:00:00Z");
        ProductTemplate onion = new ProductTemplate(onionId, "Løg", ProductCategory.VEGETABLE, Unit.PIECE, List.of(), false);
        ProductTemplate salt = new ProductTemplate(saltId, "Salt", ProductCategory.SPICE, Unit.GRAM, List.of(), false);
        Recipe existing = new Recipe(recipeId, userId, "Gammel", "Før", createdAt, createdAt,
                List.of(new dk.jamesbabz.madkursus.service.models.RecipeIngredient(UUID.randomUUID(), onion,
                        new BigDecimal("0.5"), RecipeUnit.PIECE, null, 1)),
                List.of(new dk.jamesbabz.madkursus.service.models.RecipeStep(UUID.randomUUID(), "Gammelt trin", 1)));
        when(currentUser.currentUserId()).thenReturn(userId);
        when(port.findByIdAndUserId(recipeId, userId)).thenReturn(Optional.of(existing));
        when(templates.get(onionId)).thenReturn(onion); when(templates.get(saltId)).thenReturn(salt);
        when(port.save(any())).thenAnswer(call -> call.getArgument(0));

        Recipe updated = service.update(recipeId, "Nyt navn", "Efter",
                List.of(new RecipeService.IngredientInput(saltId, new BigDecimal("2.5"), RecipeUnit.GRAM, null, 1),
                        new RecipeService.IngredientInput(onionId, BigDecimal.ONE, RecipeUnit.PIECE, "finthakket", 2)),
                List.of(new RecipeService.StepInput("Nyt første trin", 1),
                        new RecipeService.StepInput("Nyt andet trin", 2)));

        assertThat(updated.id()).isEqualTo(recipeId);
        assertThat(updated.createdAt()).isEqualTo(createdAt);
        assertThat(updated.name()).isEqualTo("Nyt navn"); assertThat(updated.description()).isEqualTo("Efter");
        assertThat(updated.ingredients()).extracting(i -> i.productTemplate().name()).containsExactly("Salt", "Løg");
        assertThat(updated.ingredients().getFirst().quantity()).isEqualByComparingTo("2.5");
        assertThat(updated.steps()).extracting(s -> s.instruction()).containsExactly("Nyt første trin", "Nyt andet trin");
        assertThat(onion.name()).isEqualTo("Løg"); assertThat(salt.name()).isEqualTo("Salt");
    }
}
