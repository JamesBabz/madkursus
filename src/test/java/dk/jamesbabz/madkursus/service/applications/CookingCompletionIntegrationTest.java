package dk.jamesbabz.madkursus.service.applications;

import dk.jamesbabz.madkursus.MadkursusApplication;
import dk.jamesbabz.madkursus.inbound.security.AuthenticatedUser;
import dk.jamesbabz.madkursus.service.exceptions.ConflictException;
import dk.jamesbabz.madkursus.service.models.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class CookingCompletionIntegrationTest {
    @Test void directRetriesPlannedConcurrencyRollbackAndReservationsUseOneNonNegativeConsumptionRule() throws Exception {
        try (var postgres = EmbeddedPostgres.start(); var context = new SpringApplicationBuilder(MadkursusApplication.class)
                .web(WebApplicationType.SERVLET).run("--server.port=0", "--spring.datasource.url=" + postgres.getJdbcUrl("postgres", "postgres"),
                        "--spring.datasource.username=postgres", "--spring.datasource.password=", "--spring.jpa.hibernate.ddl-auto=validate")) {
            var jdbc = context.getBean(JdbcTemplate.class);
            UUID user = UUID.randomUUID(), recipe = UUID.randomUUID();
            jdbc.update("insert into users(id,username,password_hash,created_at,enabled) values (?,?,?,now(),true)",user,user.toString(),"unused");
            jdbc.update("insert into recipes(id,user_id,name,created_at,updated_at) values (?,?,?,now(),now())",recipe,user,"Dinner");
            UUID[] templates = new UUID[4], products = new UUID[4];
            String[] modes = {"QUANTITY","QUANTITY","PRESENCE","UNTRACKED"}, units = {"PIECE","GRAM","GRAM","MILLILITER"};
            for (int i=0;i<4;i++) {
                templates[i]=UUID.randomUUID(); products[i]=UUID.randomUUID(); String name="Ingredient " + templates[i];
                jdbc.update("insert into product_templates(id,name,normalized_name,category,default_unit,common,default_tracking_mode) values (?,?,?,'OTHER',?,false,?)",templates[i],name,name,units[i],modes[i]);
                jdbc.update("insert into products(id,user_id,source_template_id,name,category,default_unit,inventory_tracking_mode) values (?,?,?,?,'OTHER',?,?)",products[i],user,templates[i],name,units[i],modes[i]);
                jdbc.update("insert into recipe_ingredients(id,recipe_id,product_template_id,quantity,unit,sort_order) values (?,?,?,?,?,?)",UUID.randomUUID(),recipe,templates[i],i<2?6:1,units[i],i+1);
                if (i<3) jdbc.update("insert into inventory_items(id,product_id,quantity) values (?,?,?)",UUID.randomUUID(),products[i],i<2?10:null);
            }
            var interactions = context.getBean(RecipeInteractionService.class); var plans = context.getBean(MealPlanService.class);
            authenticate(user);
            UUID completion=UUID.randomUUID();
            try (var executor=Executors.newFixedThreadPool(4)) {
                var tasks = new ArrayList<Future<RecipeCookResult>>();
                for(int i=0;i<4;i++) tasks.add(executor.submit(()->{authenticate(user);try{return interactions.cookOnce(recipe,BigDecimal.ONE,completion);}finally{SecurityContextHolder.clearContext();}}));
                for(var task:tasks) assertThat(task.get(30,TimeUnit.SECONDS).history().id()).isEqualTo(completion);
            }
            assertThat(quantity(jdbc,products[0])).isEqualByComparingTo("4"); assertThat(quantity(jdbc,products[1])).isEqualByComparingTo("4");
            assertThat(historyCount(jdbc,user)).isEqualTo(1);
            assertThatThrownBy(()->interactions.cookOnce(recipe,BigDecimal.TEN,completion)).isInstanceOf(ConflictException.class);
            assertThat(jdbc.queryForObject("select count(*) from inventory_items where product_id=? and quantity is null",Integer.class,products[2])).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from inventory_items where product_id=?",Integer.class,products[3])).isZero();

            // A failure after history has actually been inserted still rolls back history and all deductions.
            jdbc.update("update inventory_items set quantity=10 where product_id in (?,?)",products[0],products[1]);
            var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            UUID retry=UUID.randomUUID();
            assertThatThrownBy(()->transaction.execute(status->{interactions.cookOnce(recipe,BigDecimal.ONE,retry);throw new IllegalStateException("Failure after completion");})).isInstanceOf(IllegalStateException.class);
            assertThat(quantity(jdbc,products[0])).isEqualByComparingTo("10"); assertThat(quantity(jdbc,products[1])).isEqualByComparingTo("10");
            assertThat(historyCount(jdbc,user)).isEqualTo(1);
            interactions.cookOnce(recipe,BigDecimal.ONE,retry); assertThat(historyCount(jdbc,user)).isEqualTo(2);

            jdbc.update("update inventory_items set quantity=2 where product_id=?",products[0]);
            jdbc.update("update inventory_items set quantity=10 where product_id=?",products[1]);
            var plan=plans.create("Dinner plan",List.of(new RecipeSelection(recipe,BigDecimal.ONE)));
            var other=plans.create("Another plan",List.of(new RecipeSelection(recipe,BigDecimal.ONE)));
            try(var executor=Executors.newFixedThreadPool(2)) {
                var tasks=new ArrayList<Future<Boolean>>();
                for(int i=0;i<2;i++) tasks.add(executor.submit(()->{authenticate(user);try{plans.cook(plan.id(),plan.recipes().getFirst().id());return true;}catch(ConflictException expected){return false;}finally{SecurityContextHolder.clearContext();}}));
                int success=0;for(var task:tasks)if(task.get(30,TimeUnit.SECONDS))success++;
                assertThat(success).isEqualTo(1);
            }
            assertThat(historyCount(jdbc,user)).isEqualTo(3);
            assertThat(jdbc.queryForObject("select count(*) from inventory_items where product_id=?",Integer.class,products[0])).isZero();
            assertThat(quantity(jdbc,products[1])).isEqualByComparingTo("4");
            assertThat(plans.get(plan.id()).recipes().getFirst().status()).isEqualTo(PlannedRecipeStatus.COOKED);
            assertThat(plans.get(other.id()).recipes().getFirst().status()).isEqualTo(PlannedRecipeStatus.PLANNED);
            var requirement=plans.requirements(other.id()).requirements().stream().filter(r->r.productTemplate().id().equals(templates[1])).findFirst().orElseThrow();
            assertThat(requirement.physicalQuantity()).isEqualByComparingTo("4"); assertThat(requirement.missingQuantity()).isEqualByComparingTo("2");
            var reserved=interactions.calculate(List.of(new RecipeSelection(recipe,BigDecimal.ONE))).requirements().stream().filter(r->r.productTemplate().id().equals(templates[1])).findFirst().orElseThrow();
            assertThat(reserved.reservedQuantity()).isEqualByComparingTo("6"); assertThat(reserved.availableQuantity()).isZero();
            assertThat(jdbc.queryForObject("select count(*) from inventory_items where quantity<0",Integer.class)).isZero();
            SecurityContextHolder.clearContext();
        } finally {SecurityContextHolder.clearContext();}
    }
    private static void authenticate(UUID id) {var principal=new AuthenticatedUser(id,id.toString(),"unused",true);SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,"unused",principal.getAuthorities()));}
    private static BigDecimal quantity(JdbcTemplate jdbc,UUID product) {return jdbc.queryForObject("select quantity from inventory_items where product_id=?",BigDecimal.class,product);}
    private static int historyCount(JdbcTemplate jdbc,UUID user) {return jdbc.queryForObject("select count(*) from recipe_cook_history where user_id=?",Integer.class,user);}
}
