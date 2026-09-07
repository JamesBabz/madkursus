package dk.jamesbabz.madkursus.tools;

import dk.jamesbabz.madkursus.MadkursusApplication;
import dk.jamesbabz.madkursus.service.applications.DtuNutritionAdminService;
import dk.jamesbabz.madkursus.service.applications.NutritionAiReviewImportService;
import java.nio.file.Path;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

public final class NutritionAiReviewImportCli {
 private NutritionAiReviewImportCli(){}
 public static void main(String[] args) throws Exception{
  String configured=System.getProperty("nutritionReviewFile");if(configured==null||configured.isBlank())throw new IllegalArgumentException("Pass -PreviewFile=<nutrition-ai-review-result.json>");
  try(var context=new SpringApplicationBuilder(MadkursusApplication.class).web(WebApplicationType.SERVLET).run("--server.port=0")){
   var dtu=context.getBean(DtuNutritionAdminService.class);if(dtu.latestVersion()==null)dtu.importBundled();
   var result=context.getBean(NutritionAiReviewImportService.class).importFile(Path.of(configured));
   System.out.println("\nNutrition AI review import\n");System.out.println("MATCH/HIGH decisions found: "+result.matchHighFound());System.out.println("Applied: "+result.applied());System.out.println("Rejected by validation: "+result.rejectedByValidation());System.out.println("Skipped already approved: "+result.skippedAlreadyApproved());
   System.out.println("ProductTemplates changed: "+String.join(", ",result.changedProductTemplates()));
   System.out.printf("Coverage before: %d/%d recipes complete; %d/%d ingredients covered%n",result.beforeCoverage().completeRecipes(),result.beforeCoverage().totalRecipes(),result.beforeCoverage().coveredIngredients(),result.beforeCoverage().totalIngredients());
   System.out.printf("Coverage after: %d/%d recipes complete; %d/%d ingredients covered%n",result.afterCoverage().completeRecipes(),result.afterCoverage().totalRecipes(),result.afterCoverage().coveredIngredients(),result.afterCoverage().totalIngredients());
   System.out.println("Remaining unresolved: "+result.remainingUnresolved());result.validationErrors().forEach(error->System.out.println("REJECTED: "+error));
   if(result.rejectedByValidation()>0)throw new IllegalStateException("No mappings were applied because review validation failed");
  }
 }
}
