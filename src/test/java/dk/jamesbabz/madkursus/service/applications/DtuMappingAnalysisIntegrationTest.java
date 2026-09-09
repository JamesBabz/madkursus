package dk.jamesbabz.madkursus.service.applications;

import dk.jamesbabz.madkursus.MadkursusApplication;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import static org.assertj.core.api.Assertions.assertThat;

class DtuMappingAnalysisIntegrationTest {
 @Test void reportsCurrentCatalogAndRecipeFocusedDistribution() throws Exception {
  try(var postgres=EmbeddedPostgres.start();var context=new SpringApplicationBuilder(MadkursusApplication.class).web(WebApplicationType.SERVLET).run("--server.port=0","--spring.datasource.url="+postgres.getJdbcUrl("postgres","postgres"),"--spring.datasource.username=postgres","--spring.datasource.password=","--spring.jpa.hibernate.ddl-auto=validate")){
   var dtu=context.getBean(DtuNutritionAdminService.class);dtu.importBundled();
   var nutrition=context.getBean(NutritionAdminService.class);var entries=nutrition.list("ALL","");
   var classifications=new EnumMap<DtuNutritionAdminService.MatchClassification,Integer>(DtuNutritionAdminService.MatchClassification.class);
   int none=0,one=0,twoThree=0,fourPlus=0,autoResolvable=0;
   for(var entry:entries){var suggestion=dtu.suggest(entry.template());classifications.merge(suggestion.classification(),1,Integer::sum);if(suggestion.resolution()==DtuNutritionAdminService.Resolution.AUTO_EQUIVALENT_CARBOHYDRATE&&entry.template().nutritionData()==null)autoResolvable++;int size=suggestion.candidates().size();if(size==0)none++;else if(size==1)one++;else if(size<=3)twoThree++;else fourPlus++;}
   var used=entries.stream().filter(entry->entry.recipeTemplateUsageCount()>0).toList();
   long usedMissing=used.stream().filter(entry->entry.template().nutritionData()==null).count();
   long usedAuto=used.stream().filter(entry->entry.template().nutritionData()==null&&dtu.suggest(entry.template()).resolution()==DtuNutritionAdminService.Resolution.AUTO_EQUIVALENT_CARBOHYDRATE).count();
   StringBuilder report=new StringBuilder("# Recipe-used DTU candidate quality report\n\n");
   report.append("Generated from a clean database, canonical active RecipeTemplates and bundled DTU Frida 5.5.\n\n");
   report.append("| ProductTemplate | Top candidate | Classification | Resolution | Why | Alternatives |\n|---|---|---|---|---|---|\n");
   for(var entry:used){var suggestion=dtu.suggest(entry.template());var candidates=suggestion.candidates();String top=candidates.isEmpty()?"—":candidates.getFirst().food().danishName();String why=candidates.isEmpty()?suggestion.reason():candidates.getFirst().reason();String alternatives=candidates.stream().skip(1).map(candidate->candidate.food().danishName()).collect(java.util.stream.Collectors.joining("; "));report.append('|').append(entry.template().name()).append('|').append(top).append('|').append(suggestion.classification()).append('|').append(suggestion.resolution()).append('|').append(why).append('|').append(alternatives).append("|\n");}
   report.append("\nGlobal classification: ").append(classifications).append("\n\nAuto-resolvable missing ProductTemplates: ").append(autoResolvable).append("\n\nRecipe-used missing: ").append(usedMissing).append("; auto-resolvable: ").append(usedAuto).append("\n\nCandidate list sizes: none=").append(none).append(", one=").append(one).append(", two-to-three=").append(twoThree).append(", four-plus=").append(fourPlus).append("\n");
   Path output=Path.of("build/reports/dtu-recipe-used-candidates.md");Files.createDirectories(output.getParent());Files.writeString(output,report);
   System.out.printf("MAPPING_ANALYSIS %s candidates={none=%d,one=%d,twoThree=%d,fourPlus=%d} auto=%d used=%d usedMissing=%d usedAuto=%d report=%s%n",classifications,none,one,twoThree,fourPlus,autoResolvable,used.size(),usedMissing,usedAuto,output);
   // This is an analysis test, not a snapshot of the growing recipe/product catalogs.
   // Derive expected usage independently from persisted active recipes, counting a
   // product once per recipe even when multiple ingredient rows reference it.
   var jdbc=context.getBean(JdbcTemplate.class);
   var expectedUsage=new HashMap<UUID,Integer>();
   jdbc.query("""
       SELECT i.product_template_id, COUNT(DISTINCT r.id) AS recipe_count
       FROM recipe_template_ingredients i
       JOIN recipe_templates r ON r.id=i.recipe_template_id
       WHERE r.active=true
       GROUP BY i.product_template_id
       """, (org.springframework.jdbc.core.RowCallbackHandler) row ->
       expectedUsage.put(row.getObject("product_template_id",UUID.class),row.getInt("recipe_count")));
   var productIds=jdbc.query("SELECT id FROM product_templates",(row,index)->row.getObject("id",UUID.class));
   assertThat(entries).isNotEmpty();
   assertThat(entries).extracting(entry->entry.template().id()).containsExactlyInAnyOrderElementsOf(productIds);
   assertThat(used).extracting(entry->entry.template().id()).containsExactlyInAnyOrderElementsOf(expectedUsage.keySet());
   for(var entry:entries)assertThat(entry.recipeTemplateUsageCount()).as("Distinct active recipe usage for %s",entry.key())
       .isEqualTo(expectedUsage.getOrDefault(entry.template().id(),0));
   assertThat(used).extracting(NutritionAdminService.Entry::key)
       .contains("MADLAVNINGSFLOEDE","ROED_PEBERFRUGT");
   assertThat(entries).isSortedAccordingTo(Comparator.comparingInt(NutritionAdminService.Entry::recipeTemplateUsageCount)
       .reversed().thenComparing(entry->entry.template().name()));
   assertThat(classifications.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(entries.size());
   assertThat(none+one+twoThree+fourPlus).isEqualTo(entries.size());
   assertThat(autoResolvable).isBetween(0,(int)entries.stream().filter(entry->entry.template().nutritionData()==null).count());
   assertThat(usedMissing).isBetween(0L,(long)used.size());
   assertThat(usedAuto).isBetween(0L,usedMissing);
  }
 }
}
