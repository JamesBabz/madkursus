package dk.jamesbabz.madkursus.tools.recipetemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import dk.jamesbabz.madkursus.MadkursusApplication;
import dk.jamesbabz.madkursus.service.applications.RecipeTemplateService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecipeTemplateLocalImportIntegrationTest {
    @TempDir Path root;
    @Test void realCookingDraftImportsViaAdminApiThenFreshPostgresAppliesAndRendersIt()throws Exception {
        for(String dir:List.of("src/main/resources/seed","src/main/resources/db/migration","src/main/java/db/migration")) {
            Path target=root.resolve(dir);Files.createDirectories(target);
            try(var files=Files.list(Path.of(dir))){for(var file:files.filter(Files::isRegularFile).toList())Files.copy(file,target.resolve(file.getFileName()));}
        }
        String draft=Files.readString(Path.of("src/main/resources/recipe-templates/karbonader-med-kartofler-guleroedder-og-brun-pandesovs.json"));
        String before=Files.readString(root.resolve("src/main/resources/seed/recipe-templates.json"));
        String migration;
        try(var postgres=EmbeddedPostgres.start();var context=new SpringApplicationBuilder(MadkursusApplication.class).web(WebApplicationType.SERVLET)
                .run("--server.port=0","--spring.datasource.url="+postgres.getJdbcUrl("postgres","postgres"),"--spring.datasource.username=postgres","--spring.datasource.password=",
                        "--madkursus.recipe-template-import.enabled=true","--madkursus.recipe-template-import.project-directory="+root)) {
            var mvc=MockMvcBuilders.webAppContextSetup((org.springframework.web.context.WebApplicationContext)context).apply(springSecurity()).build();
            String base="/v1/admin/recipe-template-import/";
            var validation=mvc.perform(post(base+"validate").with(user("admin").roles("ADMIN")).with(csrf()).contentType("text/plain").content(draft)).andExpect(status().isOk()).andReturn();
            var json=new ObjectMapper();assertThat(json.readTree(validation.getResponse().getContentAsString()).get("valid").asBoolean()).isTrue();
            assertThat(Files.readString(root.resolve("src/main/resources/seed/recipe-templates.json"))).isEqualTo(before);
            var imported=mvc.perform(post(base+"import").with(user("admin").roles("ADMIN")).with(csrf()).contentType("text/plain").content(draft)).andExpect(status().isOk()).andReturn();
            var result=json.readTree(imported.getResponse().getContentAsString());assertThat(result.get("imported").asBoolean()).as(result.toString()).isTrue();assertThat(result.get("action").asText()).isEqualTo("UPDATE");migration=result.get("migrationFile").asText();
        }
        Path generated=root.resolve("generated");Files.createDirectories(generated);Files.copy(root.resolve("src/main/resources/db/migration").resolve(migration),generated.resolve(migration));
        try(var postgres=EmbeddedPostgres.start();var context=new SpringApplicationBuilder(MadkursusApplication.class).web(WebApplicationType.SERVLET)
                .run("--server.port=0","--spring.datasource.url="+postgres.getJdbcUrl("postgres","postgres"),"--spring.datasource.username=postgres","--spring.datasource.password=",
                        "--spring.flyway.locations=classpath:db/migration,filesystem:"+generated,"--spring.jpa.hibernate.ddl-auto=validate")) {
            var principal=new dk.jamesbabz.madkursus.inbound.security.AuthenticatedUser(UUID.randomUUID(),"reviewer","unused",true,true);
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
            var recipe=context.getBean(RecipeTemplateService.class).getRendered(UUID.fromString("bc01b52c-f60e-361b-9f13-ed11243413b6"),2);
            assertThat(recipe.name()).contains("Karbonader");assertThat(recipe.ingredients()).isNotEmpty();assertThat(recipe.steps()).isNotEmpty();
            assertThat(recipe.steps()).anyMatch(s->s.renderedProcess()!=null&&!s.renderedProcess().instructions().isEmpty());
            assertThat(context.getBean(org.flywaydb.core.Flyway.class).info().pending()).isEmpty();
            assertThat(context.getBean(org.flywaydb.core.Flyway.class).info().current().getScript()).isEqualTo(migration);
        } finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }
}
