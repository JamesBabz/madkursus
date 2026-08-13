package dk.jamesbabz.madkursus.service.applications;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import dk.jamesbabz.madkursus.service.exceptions.ConflictException;
import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.ports.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeTemplateServiceTest {
    @Mock RecipeTemplatePort templates; @Mock RecipePort recipes;
    @Mock RecipeService recipeService; @Mock CurrentUserProvider currentUser;

    @Test void copiesAuthoritativeTemplateAndPreventsDuplicateForSameUser() {
        UUID userId=UUID.randomUUID(), templateId=UUID.randomUUID();
        ProductTemplate onion=new ProductTemplate(UUID.randomUUID(),"Løg",ProductCategory.VEGETABLE,Unit.PIECE,List.of(),false);
        RecipeTemplate template=new RecipeTemplate(templateId,"Kødsovs","Nem",true,Instant.now(),Instant.now(),
                List.of(new RecipeTemplateIngredient(UUID.randomUUID(),onion,new BigDecimal("0.5"),RecipeUnit.PIECE,"finthakket",1)),
                List.of(new RecipeTemplateStep(UUID.randomUUID(),"Hak løget.",1)));
        Recipe copied=new Recipe(UUID.randomUUID(),userId,templateId,"Kødsovs","Nem",Instant.now(),Instant.now(),List.of(),List.of());
        when(templates.findById(templateId)).thenReturn(Optional.of(template));
        when(currentUser.currentUserId()).thenReturn(userId);
        when(recipes.findByUserIdAndSourceTemplateId(userId,templateId)).thenReturn(Optional.empty(),Optional.of(copied));
        when(recipeService.createFromTemplate(template)).thenReturn(copied);
        RecipeTemplateService service=new RecipeTemplateService(templates,recipes,recipeService,currentUser);
        assertThat(service.copy(templateId)).isSameAs(copied);
        verify(recipeService).createFromTemplate(template);
        assertThatThrownBy(()->service.copy(templateId)).isInstanceOf(ConflictException.class);
    }

    @Test void duplicateDetectionIsScopedToCurrentUser() {
        UUID templateId=UUID.randomUUID(), userB=UUID.randomUUID();
        RecipeTemplate template=new RecipeTemplate(templateId,"Frikadeller",null,true,Instant.now(),Instant.now(),List.of(),List.of());
        Recipe copy=new Recipe(UUID.randomUUID(),userB,templateId,"Frikadeller",null,Instant.now(),Instant.now(),List.of(),List.of());
        when(currentUser.currentUserId()).thenReturn(userB); when(templates.findById(templateId)).thenReturn(Optional.of(template));
        when(recipes.findByUserIdAndSourceTemplateId(userB,templateId)).thenReturn(Optional.empty()); when(recipeService.createFromTemplate(template)).thenReturn(copy);
        assertThat(new RecipeTemplateService(templates,recipes,recipeService,currentUser).copy(templateId).userId()).isEqualTo(userB);
    }

    @Test void searchAndDetailOnlyUseTheSharedTemplateCatalog() {
        UUID templateId=UUID.randomUUID();
        RecipeTemplate template=new RecipeTemplate(templateId,"Tomatsuppe",null,true,Instant.now(),Instant.now(),List.of(),List.of());
        when(templates.search("tomat")).thenReturn(List.of(template));
        when(templates.findById(templateId)).thenReturn(Optional.of(template));
        RecipeTemplateService service=new RecipeTemplateService(templates,recipes,recipeService,currentUser);

        assertThat(service.search("tomat")).containsExactly(template);
        assertThat(service.get(templateId)).isSameAs(template);
        verifyNoInteractions(recipes,recipeService,currentUser);
    }
}
