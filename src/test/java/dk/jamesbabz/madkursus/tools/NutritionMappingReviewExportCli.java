package dk.jamesbabz.madkursus.tools;

import dk.jamesbabz.madkursus.MadkursusApplication;
import dk.jamesbabz.madkursus.service.applications.DtuNutritionAdminService;
import dk.jamesbabz.madkursus.service.applications.NutritionMappingReviewExportService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.Path;
import java.time.Instant;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

public final class NutritionMappingReviewExportCli {
    private NutritionMappingReviewExportCli(){}
    public static void main(String[] args) throws Exception{
        try(var postgres=EmbeddedPostgres.start();var context=new SpringApplicationBuilder(MadkursusApplication.class)
                .web(WebApplicationType.SERVLET).run("--server.port=0","--spring.datasource.url="+postgres.getJdbcUrl("postgres","postgres"),
                        "--spring.datasource.username=postgres","--spring.datasource.password=",
                        "--spring.jpa.hibernate.ddl-auto=validate")){
            context.getBean(DtuNutritionAdminService.class).importBundled();
            var result=context.getBean(NutritionMappingReviewExportService.class)
                    .export(Path.of("build/nutrition-review"),Instant.now());var stats=result.statistics();
            System.out.println("\nNutrition AI review export\n");
            System.out.println("Unresolved ProductTemplates: "+stats.unresolved());
            System.out.println("Used by recipes: "+stats.usedByRecipes());
            System.out.println("With automatic candidates: "+stats.withAutomaticCandidates());
            System.out.println("With broad-search candidates only: "+stats.withBroadSearchCandidatesOnly());
            System.out.println("Without any useful DTU candidate: "+stats.withoutUsefulCandidate());
            System.out.println("\nCandidate counts:");
            System.out.println("1 candidate: "+stats.oneCandidate());System.out.println("2-3 candidates: "+stats.twoToThreeCandidates());
            System.out.println("4-10 candidates: "+stats.fourToTenCandidates());System.out.println("0 candidates: "+stats.zeroCandidates());
            System.out.println("\nOutput:\n"+result.json());
        }
    }
}
