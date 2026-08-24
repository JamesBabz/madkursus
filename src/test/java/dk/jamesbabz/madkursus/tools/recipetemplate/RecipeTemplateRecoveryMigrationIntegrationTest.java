package dk.jamesbabz.madkursus.tools.recipetemplate;

import dk.jamesbabz.madkursus.MadkursusApplication;
import dk.jamesbabz.madkursus.service.applications.RecipeTemplateService;
import dk.jamesbabz.madkursus.service.applications.ProductTemplateService;
import dk.jamesbabz.madkursus.service.applications.ProductService;
import dk.jamesbabz.madkursus.service.applications.InventoryService;
import dk.jamesbabz.madkursus.service.applications.RecipeInteractionService;
import dk.jamesbabz.madkursus.service.applications.ShoppingListService;
import dk.jamesbabz.madkursus.service.models.RecipeStepType;
import dk.jamesbabz.madkursus.service.models.RecipeSelection;
import dk.jamesbabz.madkursus.inbound.security.AuthenticatedUser;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.Connection;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecipeTemplateRecoveryMigrationIntegrationTest {
    @TempDir Path temp;

    @Test void emptyDatabaseMigratesThroughCurrentWithImmutableV21SnapshotAndTemplateRenders()throws Exception {
        try(EmbeddedPostgres postgres=EmbeddedPostgres.start()){
            var dataSource=postgres.getPostgresDatabase();
            Path snapshot=temp.resolve("snapshot/seed/recipe-templates.json");Files.createDirectories(snapshot.getParent());var mapper=new ObjectMapper();ObjectNode root=(ObjectNode)mapper.readTree(Path.of("src/main/resources/seed/recipe-templates.json").toFile());root.withArray("recipes").addObject().put("key","FUTURE_TEMPLATE_THAT_V21_MUST_IGNORE").put("name","Fremtidig template");mapper.writeValue(snapshot.toFile(),root);
            URL[] urls={temp.resolve("snapshot").toUri().toURL(),Path.of("build/classes/java/main").toUri().toURL(),Path.of("build/resources/main").toUri().toURL()};try(var migrationLoader=new MigrationClassLoader(urls,getClass().getClassLoader())){var result=Flyway.configure(migrationLoader).dataSource(dataSource).locations("classpath:db/migration").target("21").load().migrate();assertThat(result.targetSchemaVersion.toString()).isEqualTo("21");}
            var throughV32=Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("32").load().migrate();assertThat(throughV32.targetSchemaVersion.toString()).isEqualTo("32");
            assertHistoricalV32State(dataSource.getConnection());
            assertThat(Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("32").load().validateWithResult().validationSuccessful).isTrue();
            var throughV33=Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("33").load().migrate();assertThat(throughV33.targetSchemaVersion.toString()).isEqualTo("33");
            try(Connection connection=dataSource.getConnection();var row=connection.createStatement().executeQuery("SELECT factor FROM product_template_unit_conversions WHERE template_id='79d3cfe2-9723-3844-b21f-f7b543d13aa1' AND from_unit='TABLESPOON' AND to_unit='GRAM'")){assertThat(row.next()).isTrue();assertThat(row.getBigDecimal(1)).isEqualByComparingTo("9");}
            var throughV34=Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("34").load().migrate();assertThat(throughV34.targetSchemaVersion.toString()).isEqualTo("34");
            try(Connection connection=dataSource.getConnection();var row=connection.createStatement().executeQuery("SELECT to_regclass('spring_session'),to_regclass('spring_session_attributes')")){assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("spring_session");assertThat(row.getString(2)).isEqualTo("spring_session_attributes");}
            var throughV35=Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("35").load().migrate();assertThat(throughV35.targetSchemaVersion.toString()).isEqualTo("35");
            assertCurrentRapsolieState(dataSource.getConnection());
            assertThat(Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().validateWithResult().validationSuccessful).isTrue();
            try(Connection connection=dataSource.getConnection()){try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT COUNT(*), COUNT(DISTINCT id) FROM recipe_template_steps WHERE recipe_template_id='f94ea16d-7040-3bbc-9432-3455cc0c9360'")){assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isEqualTo(11).isEqualTo(rows.getInt(2));}try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT COUNT(*), COUNT(DISTINCT id) FROM recipe_template_process_bindings WHERE recipe_template_step_id IN (SELECT id FROM recipe_template_steps WHERE recipe_template_id='f94ea16d-7040-3bbc-9432-3455cc0c9360')")){assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isPositive().isEqualTo(rows.getInt(2));}try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT COUNT(*) FROM recipe_templates")){assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isEqualTo(16);}}
            try(Connection connection=dataSource.getConnection();var statement=connection.createStatement();var row=statement.executeQuery("SELECT recipe_ingredient_id,product_template_id,quantity,unit FROM recipe_template_process_bindings WHERE parameter_key='FAT' AND recipe_template_step_id IN (SELECT id FROM recipe_template_steps WHERE recipe_template_id='f94ea16d-7040-3bbc-9432-3455cc0c9360')")){assertThat(row.next()).isTrue();assertThat(row.getObject(1)).isNotNull();assertThat(row.getObject(2)).hasToString("ce84c904-58c9-3c93-b8af-35eb4acd1499");assertThat(row.getBigDecimal(3)).isEqualByComparingTo("0.5");assertThat(row.getString(4)).isEqualTo("TABLESPOON");}
            var userId=java.util.UUID.randomUUID();try(Connection connection=dataSource.getConnection();var insert=connection.prepareStatement("INSERT INTO users(id,username,password_hash,created_at,enabled) VALUES (?,?,?,CURRENT_TIMESTAMP,true)")){insert.setObject(1,userId);insert.setString(2,"integration-test");insert.setString(3,"unused");insert.executeUpdate();}
            String url=postgres.getJdbcUrl("postgres","postgres");try(var context=new SpringApplicationBuilder(MadkursusApplication.class).web(WebApplicationType.SERVLET).run("--server.port=0","--spring.datasource.url="+url,"--spring.datasource.username=postgres","--spring.datasource.password=","--spring.jpa.hibernate.ddl-auto=validate")){assertThat(context.isActive()).isTrue();var principal=new AuthenticatedUser(userId,"integration-test","",true);SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));try{RecipeTemplateService service=context.getBean(RecipeTemplateService.class);var id=java.util.UUID.fromString("f94ea16d-7040-3bbc-9432-3455cc0c9360");assertRenderedFat(service,id,1,"½ spsk");assertRenderedFat(service,id,2,"1 spsk");assertRenderedFat(service,id,4,"2 spsk");var flourId=java.util.UUID.fromString("79d3cfe2-9723-3844-b21f-f7b543d13aa1");var flour=context.getBean(ProductTemplateService.class).get(flourId);assertThat(flour.conversions()).singleElement().satisfies(rule->{assertThat(rule.fromUnit().name()).isEqualTo("TABLESPOON");assertThat(rule.factor()).isEqualByComparingTo("9");});ProductService products=context.getBean(ProductService.class);var flourProduct=products.createFromTemplate(flour.id(),flour.name(),flour.category(),flour.defaultUnit(),flour.defaultTrackingMode());context.getBean(InventoryService.class).add(flourProduct.id(),new java.math.BigDecimal("5"));var copied=service.copy(id);RecipeInteractionService interactions=context.getBean(RecipeInteractionService.class);var calculation=interactions.addMissingToShoppingList(java.util.List.of(new RecipeSelection(copied.id(),new java.math.BigDecimal("2"))));var flourRequirement=calculation.requirements().stream().filter(value->value.productTemplate().id().equals(flourId)).findFirst().orElseThrow();assertThat(flourRequirement.warning()).isNull();assertThat(flourRequirement.requiredQuantity()).isEqualByComparingTo("18");assertThat(flourRequirement.missingQuantity()).isEqualByComparingTo("13");assertThat(flourRequirement.displayRequiredQuantity()).isEqualByComparingTo("2");assertThat(flourRequirement.displayRequiredUnit().name()).isEqualTo("TABLESPOON");assertThat(calculation.requirements()).noneMatch(value->"Vand".equals(value.productTemplate().name()));assertThat(context.getBean(ShoppingListService.class).getAll()).anySatisfy(item->{if(item.product().sourceTemplateId()!=null&&item.product().sourceTemplateId().equals(flourId))assertThat(item.quantity()).isEqualByComparingTo("13");});context.getBean(dk.jamesbabz.madkursus.service.applications.RecipeService.class).delete(copied.id());assertThatThrownBy(()->context.getBean(dk.jamesbabz.madkursus.service.applications.RecipeService.class).get(copied.id())).isInstanceOf(dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException.class);verifyRecipeDeletionLifecycle(context,id,userId,dataSource);}finally{SecurityContextHolder.clearContext();}}
            try(Connection connection=dataSource.getConnection();var statement=connection.createStatement();var row=statement.executeQuery("SELECT s.quantity FROM shopping_list_items s JOIN products p ON p.id=s.product_id WHERE p.source_template_id='79d3cfe2-9723-3844-b21f-f7b543d13aa1' AND s.purchased=false")){assertThat(row.next()).isTrue();assertThat(row.getBigDecimal(1)).isEqualByComparingTo("13");assertThat(row.next()).isFalse();}
        }
    }

    private void assertRenderedFat(RecipeTemplateService service,java.util.UUID id,int portions,String amount){var template=service.getRendered(id,portions);var frying=template.steps().stream().filter(step->step.type()==RecipeStepType.PROCESS&&step.renderedProcess()!=null&&"Steg kødboller".equals(step.renderedProcess().processName())).findFirst().orElseThrow();assertThat(frying.renderedProcess().instructions()).anySatisfy(instruction->assertThat(instruction).contains("Rapsolie",amount));}

    private void verifyRecipeDeletionLifecycle(org.springframework.context.ApplicationContext context,UUID templateId,UUID userId,javax.sql.DataSource dataSource)throws Exception {
        RecipeTemplateService templates=context.getBean(RecipeTemplateService.class);
        var recipes=context.getBean(dk.jamesbabz.madkursus.service.applications.RecipeService.class);
        var plans=context.getBean(dk.jamesbabz.madkursus.service.applications.MealPlanService.class);

        var plain=recipes.create("Aldrig planlagt",null,java.util.List.of(),java.util.List.of());
        recipes.delete(plain.id());
        assertThatThrownBy(()->recipes.get(plain.id())).isInstanceOf(dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException.class);

        var active=templates.copy(templateId);
        var activePlan=plans.create("Aktiv madplan",java.util.List.of(new RecipeSelection(active.id(),java.math.BigDecimal.ONE)));
        assertThatThrownBy(()->recipes.delete(active.id())).isInstanceOf(dk.jamesbabz.madkursus.service.exceptions.ConflictException.class).hasMessageContaining("aktiv madplan");
        plans.removeRecipe(activePlan.id(),activePlan.recipes().getFirst().id());
        recipes.delete(active.id());

        var historical=templates.copy(templateId);
        var historyPlan=plans.create("Historisk madplan",java.util.List.of(new RecipeSelection(historical.id(),java.math.BigDecimal.ONE)));
        plans.skip(historyPlan.id(),historyPlan.recipes().getFirst().id());
        recipes.delete(historical.id());
        var retained=plans.get(historyPlan.id()).recipes().getFirst();
        assertThat(retained.status()).isEqualTo(dk.jamesbabz.madkursus.service.models.PlannedRecipeStatus.SKIPPED);
        assertThat(retained.recipe()).isNull();

        var cookedHistoryRecipe=recipes.create("Med kogehistorik",null,java.util.List.of(),java.util.List.of());
        UUID historyId=UUID.randomUUID();
        try(Connection connection=dataSource.getConnection();var insert=connection.prepareStatement("INSERT INTO recipe_cook_history(id,user_id,recipe_id,recipe_name,portions,cooked_at) VALUES (?,?,?,?,1,CURRENT_TIMESTAMP)")){insert.setObject(1,historyId);insert.setObject(2,userId);insert.setObject(3,cookedHistoryRecipe.id());insert.setString(4,cookedHistoryRecipe.name());insert.executeUpdate();}
        recipes.delete(cookedHistoryRecipe.id());
        try(Connection connection=dataSource.getConnection();var row=connection.createStatement().executeQuery("SELECT recipe_id FROM recipe_cook_history WHERE id='"+historyId+"'")){assertThat(row.next()).isTrue();assertThat(row.getObject(1)).isNull();}
    }

    private void assertHistoricalV32State(Connection connection)throws Exception {try(connection;var row=connection.createStatement().executeQuery("SELECT i.id,i.product_template_id,b.recipe_ingredient_id,b.product_template_id FROM recipe_template_ingredients i JOIN recipe_template_process_bindings b ON b.recipe_ingredient_id=i.id WHERE i.recipe_template_id='f94ea16d-7040-3bbc-9432-3455cc0c9360' AND i.sort_order=9 AND b.id='89ded771-1aca-35ff-8a24-d6225c955c98'")){assertThat(row.next()).isTrue();assertThat(row.getObject(1)).hasToString("18943e10-2294-31d5-8612-e64d1f7864f5");assertThat(row.getObject(2)).hasToString("4b63577c-a7ef-327a-acef-6ef6010b7d6a");assertThat(row.getObject(3)).hasToString("18943e10-2294-31d5-8612-e64d1f7864f5");assertThat(row.getObject(4)).hasToString("4b63577c-a7ef-327a-acef-6ef6010b7d6a");assertThat(row.next()).isFalse();}}
    private void assertCurrentRapsolieState(Connection connection)throws Exception {try(connection){try(var row=connection.createStatement().executeQuery("SELECT t.id,i.id,i.product_template_id,b.recipe_ingredient_id,b.product_template_id FROM recipe_templates t JOIN recipe_template_ingredients i ON i.recipe_template_id=t.id JOIN recipe_template_process_bindings b ON b.recipe_ingredient_id=i.id WHERE t.id='f94ea16d-7040-3bbc-9432-3455cc0c9360' AND i.sort_order=9 AND b.id='89ded771-1aca-35ff-8a24-d6225c955c98'")){assertThat(row.next()).isTrue();assertThat(row.getObject(1)).hasToString("f94ea16d-7040-3bbc-9432-3455cc0c9360");assertThat(row.getObject(2)).hasToString("ead0c449-549a-3bbc-85f4-7e822e817aa0");assertThat(row.getObject(3)).hasToString("ce84c904-58c9-3c93-b8af-35eb4acd1499");assertThat(row.getObject(4)).hasToString("ead0c449-549a-3bbc-85f4-7e822e817aa0");assertThat(row.getObject(5)).hasToString("ce84c904-58c9-3c93-b8af-35eb4acd1499");assertThat(row.next()).isFalse();}try(var old=connection.createStatement().executeQuery("SELECT COUNT(*) FROM recipe_template_ingredients WHERE id='18943e10-2294-31d5-8612-e64d1f7864f5'")){assertThat(old.next()).isTrue();assertThat(old.getInt(1)).isZero();}}}

    private static final class MigrationClassLoader extends URLClassLoader {
        MigrationClassLoader(URL[] urls,ClassLoader parent){super(urls,parent);}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {if(name.startsWith("db.migration.")){synchronized(getClassLoadingLock(name)){Class<?> loaded=findLoadedClass(name);if(loaded==null)try{loaded=findClass(name);}catch(ClassNotFoundException ignored){}if(loaded!=null){if(resolve)resolveClass(loaded);return loaded;}}}return super.loadClass(name,resolve);}
        @Override public URL getResource(String name){if("seed/recipe-templates.json".equals(name)){URL value=findResource(name);if(value!=null)return value;}return super.getResource(name);}
    }
}
