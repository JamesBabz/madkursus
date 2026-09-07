package dk.jamesbabz.madkursus.service.applications;

import com.fasterxml.jackson.databind.ObjectMapper;
import dk.jamesbabz.madkursus.service.models.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NutritionAiReviewImportServiceTest {
 @TempDir Path temporary;
 @Test void appliesOnlyMatchHighAfterCanonicalValidation() throws Exception{
  var nutrition=mock(NutritionAdminService.class);var dtu=mock(DtuNutritionAdminService.class);var mapper=new ObjectMapper().findAndRegisterModules();var product=template("Target");
  when(nutrition.list("ALL","")).thenReturn(List.of(new NutritionAdminService.Entry(product,"TARGET",NutritionAdminService.Status.MISSING,0)));when(nutrition.list("MISSING","")).thenReturn(List.of());when(nutrition.coverage()).thenReturn(List.of());
  var food=new DtuNutritionAdminService.Food("5.5","716","Løg, rå",BigDecimal.valueOf(5.3),"rå",LocalDate.of(2025,1,1),"https://example.test");when(dtu.search("716")).thenReturn(List.of(food));
  Path input=temporary.resolve("review.json");mapper.writeValue(input.toFile(),new NutritionAiReviewImportService.ReviewResult(List.of(new NutritionAiReviewImportService.Decision("TARGET","MATCH","DTU","716","HIGH","approved"),new NutritionAiReviewImportService.Decision("TARGET","REVIEW",null,null,"MEDIUM","ignore"),new NutritionAiReviewImportService.Decision("TARGET","NO_SAFE_MATCH",null,null,"HIGH","ignore"))));
  var result=new NutritionAiReviewImportService(nutrition,dtu,mapper).importFile(input);assertThat(result.matchHighFound()).isOne();assertThat(result.applied()).isOne();assertThat(result.changedProductTemplates()).containsExactly("TARGET");verify(dtu).approveReviewed(argThat(value->value.foodId().equals("716")),eq(nutrition),eq("approved"));
 }
 @Test void anyInvalidSelectedDecisionPreventsEveryWrite() throws Exception{
  var nutrition=mock(NutritionAdminService.class);var dtu=mock(DtuNutritionAdminService.class);var mapper=new ObjectMapper().findAndRegisterModules();when(nutrition.list("ALL","")).thenReturn(List.of());when(nutrition.coverage()).thenReturn(List.of());
  Path input=temporary.resolve("invalid.json");mapper.writeValue(input.toFile(),new NutritionAiReviewImportService.ReviewResult(List.of(new NutritionAiReviewImportService.Decision("UNKNOWN","MATCH","DTU","999999","HIGH","bad"))));
  var result=new NutritionAiReviewImportService(nutrition,dtu,mapper).importFile(input);assertThat(result.rejectedByValidation()).isOne();assertThat(result.applied()).isZero();verify(dtu,never()).approveReviewed(any(),any(),any());
 }
 private ProductTemplate template(String name){return new ProductTemplate(UUID.randomUUID(),name,ProductCategory.OTHER,Unit.GRAM,InventoryTrackingMode.QUANTITY,List.of(),false,List.of(),null);}
}
