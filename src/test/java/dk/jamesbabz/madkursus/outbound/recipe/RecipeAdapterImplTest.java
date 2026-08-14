package dk.jamesbabz.madkursus.outbound.recipe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dk.jamesbabz.madkursus.outbound.producttemplate.details.ProductTemplateEntity;
import dk.jamesbabz.madkursus.outbound.producttemplate.mappers.ProductTemplateEntityMapper;
import dk.jamesbabz.madkursus.outbound.recipe.details.RecipeEntity;
import dk.jamesbabz.madkursus.outbound.recipe.details.RecipeIngredientEntity;
import dk.jamesbabz.madkursus.outbound.recipe.details.RecipeJpaRepository;
import dk.jamesbabz.madkursus.outbound.recipe.details.RecipeStepEntity;
import dk.jamesbabz.madkursus.service.models.ProductCategory;
import dk.jamesbabz.madkursus.service.models.ProductTemplate;
import dk.jamesbabz.madkursus.service.models.Recipe;
import dk.jamesbabz.madkursus.service.models.RecipeIngredient;
import dk.jamesbabz.madkursus.service.models.RecipeStep;
import dk.jamesbabz.madkursus.service.models.RecipeUnit;
import dk.jamesbabz.madkursus.service.models.Unit;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeAdapterImplTest {
    @Mock RecipeJpaRepository repository;
    @Mock ProductTemplateEntityMapper templateMapper;
    @Mock EntityManager entityManager;
    @Mock ProductTemplateEntity templateEntity;

    @Test
    void updateMutatesExistingParentAndFlushesRemovedChildrenBeforeAddingReplacements() {
        UUID recipeId=UUID.randomUUID(), userId=UUID.randomUUID(), templateId=UUID.randomUUID(); Instant created=Instant.now();
        ProductTemplate template=new ProductTemplate(templateId,"Løg", ProductCategory.VEGETABLE, Unit.PIECE,List.of(),false);
        RecipeEntity managed=new RecipeEntity(recipeId,userId,"Før",null,created,created);
        managed.addIngredient(new RecipeIngredientEntity(UUID.randomUUID(),templateEntity,BigDecimal.ONE,RecipeUnit.PIECE,null,1));
        managed.addStep(new RecipeStepEntity(UUID.randomUUID(),"Før",1));
        Recipe update=new Recipe(recipeId,userId,"Efter","Ny",created,created.plusSeconds(1),
                List.of(new RecipeIngredient(null,template,new BigDecimal("1.5"),RecipeUnit.PIECE,"hakket",1)),
                List.of(new RecipeStep(null,"Efter",1)));
        when(repository.findByIdAndUserId(recipeId,userId)).thenReturn(Optional.of(managed));
        when(entityManager.getReference(ProductTemplateEntity.class,templateId)).thenReturn(templateEntity);
        when(templateMapper.toModel(templateEntity)).thenReturn(template);

        Recipe result=new RecipeAdapterImpl(repository,templateMapper,entityManager).save(update);

        assertThat(result.id()).isEqualTo(recipeId); assertThat(result.name()).isEqualTo("Efter");
        assertThat(managed.getIngredients()).hasSize(1); assertThat(managed.getIngredients().getFirst().getId()).isNull();
        assertThat(managed.getSteps()).hasSize(1); assertThat(managed.getSteps().getFirst().getInstruction()).isEqualTo("Efter");
        verify(repository,times(2)).flush(); verify(repository,never()).save(any());
        verify(entityManager).getReference(ProductTemplateEntity.class,templateId);
        verifyNoMoreInteractions(entityManager);
    }

    @Test
    void newRecipePersistsAndReturnsItsSourceTemplateId() {
        UUID userId=UUID.randomUUID(), sourceId=UUID.randomUUID(); Instant now=Instant.now();
        Recipe input=new Recipe(null,userId,sourceId,"Template recipe",null,now,now,List.of(),List.of());
        when(repository.save(any())).thenAnswer(call->call.getArgument(0));

        Recipe result=new RecipeAdapterImpl(repository,templateMapper,entityManager).save(input);

        assertThat(result.sourceTemplateId()).isEqualTo(sourceId);
        verify(repository).save(argThat(entity->sourceId.equals(entity.getSourceTemplateId())));
    }

    @Test
    void deleteFlushesOwnedRecipeRemovalSoReferenceConflictsAreReportedInTheServiceTransaction() {
        UUID recipeId=UUID.randomUUID(),userId=UUID.randomUUID();
        new RecipeAdapterImpl(repository,templateMapper,entityManager).deleteByIdAndUserId(recipeId,userId);
        verify(repository).deleteByIdAndUserId(recipeId,userId); verify(repository).flush();
    }
}
