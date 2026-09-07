package dk.jamesbabz.madkursus.service.applications;

import com.fasterxml.jackson.databind.ObjectMapper;
import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.ports.ProductTemplatePort;
import dk.jamesbabz.madkursus.service.ports.RecipeTemplatePort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NutritionMappingReviewExportServiceTest {
 @Test void exportsOnlyUnresolvedInRecipePriorityOrderWithCatalogCandidatesAndNoWrites(){
  var nutrition=mock(NutritionAdminService.class);var dtu=mock(DtuNutritionAdminService.class);var recipes=mock(RecipeTemplatePort.class);var port=mock(ProductTemplatePort.class);
  ProductTemplate used=template("Used",null),unused=template("Unused",null),approved=template("Approved",new NutritionData(BigDecimal.ONE,BigDecimal.valueOf(100),RecipeUnit.GRAM,"manual",null,null,null,null,null));
  when(nutrition.list("ALL","")).thenReturn(List.of(new NutritionAdminService.Entry(unused,"UNUSED",NutritionAdminService.Status.MISSING,0),new NutritionAdminService.Entry(approved,"APPROVED",NutritionAdminService.Status.KNOWN,0),new NutritionAdminService.Entry(used,"USED",NutritionAdminService.Status.MISSING,1)));
  var food=new DtuNutritionAdminService.Food("5.5","42","Used food",BigDecimal.valueOf(4.6),"raw",LocalDate.of(2025,1,1),"https://example.test");
  var candidate=new DtuNutritionAdminService.Candidate(food,DtuNutritionAdminService.MatchClassification.REVIEW_REQUIRED,80,"same core");
  var usedSuggestion=new DtuNutritionAdminService.Suggestion(DtuNutritionAdminService.MatchClassification.REVIEW_REQUIRED,List.of(candidate),"review",DtuNutritionAdminService.Resolution.NONE,null,3,BigDecimal.valueOf(.9));
  var emptySuggestion=new DtuNutritionAdminService.Suggestion(DtuNutritionAdminService.MatchClassification.NO_MATCH,List.of(),"none",DtuNutritionAdminService.Resolution.NONE,null,0,null);
  when(dtu.reviewCandidates(used)).thenReturn(new DtuNutritionAdminService.ReviewCandidates(usedSuggestion,List.of(candidate),List.of(),new DtuNutritionAdminService.CompatibleCandidateSummary(3,BigDecimal.valueOf(4.1),BigDecimal.valueOf(5),BigDecimal.valueOf(4.5667),BigDecimal.valueOf(.9))));
  when(dtu.reviewCandidates(unused)).thenReturn(new DtuNutritionAdminService.ReviewCandidates(emptySuggestion,List.of(),List.of(),null));when(dtu.latestVersion()).thenReturn("5.5");when(dtu.search("42")).thenReturn(List.of(food));
  var ingredient=new RecipeTemplateIngredient(UUID.randomUUID(),used,BigDecimal.ONE,RecipeUnit.GRAM,null,0);when(recipes.search("")).thenReturn(List.of(new RecipeTemplate(UUID.randomUUID(),"Important recipe","",true,Instant.EPOCH,Instant.EPOCH,List.of(ingredient),List.of())));
  Instant timestamp=Instant.parse("2026-01-02T03:04:05Z");var result=new NutritionMappingReviewExportService(nutrition,dtu,recipes,new ObjectMapper().findAndRegisterModules()).generate(timestamp);
  assertThat(result.metadata().generatedAt()).isEqualTo(timestamp);assertThat(result.metadata().productTemplateCount()).isEqualTo(2);assertThat(result.items()).extracting(item->item.productTemplate().key()).containsExactly("USED","UNUSED").doesNotContain("APPROVED");
  assertThat(result.items().getFirst().automaticCandidates()).extracting(NutritionMappingReviewExportService.Candidate::foodId).containsExactly("42");assertThat(result.items().getFirst().compatibleCandidateSummary().spread()).isEqualByComparingTo("0.9");assertThat(result.items().get(1).automaticCandidates()).isEmpty();assertThat(result.items()).extracting(item->item.productTemplate().key()).doesNotHaveDuplicates();
  verify(port,never()).updateNutrition(any(),any());verify(dtu,never()).approve(any(),any());verify(dtu,never()).approveSafe(any());
 }
 private ProductTemplate template(String name,NutritionData data){return new ProductTemplate(UUID.randomUUID(),name,ProductCategory.OTHER,Unit.GRAM,InventoryTrackingMode.QUANTITY,List.of(),false,List.of(),data);}
}
