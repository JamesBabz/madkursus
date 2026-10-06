package dk.jamesbabz.madkursus.inbound.rest.recipe;

import com.fasterxml.jackson.databind.ObjectMapper;
import dk.jamesbabz.madkursus.MadkursusApplication;
import dk.jamesbabz.madkursus.inbound.security.AuthenticatedUser;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecipeAuthoringIntegrationTest {
    @Test void apiRoundTripPreservesCanonicalComponentsStructuredInstructionsEquipmentAndProcessBindings() throws Exception {
        try(var postgres=EmbeddedPostgres.start();var context=new SpringApplicationBuilder(MadkursusApplication.class)
                .web(WebApplicationType.SERVLET).run("--server.port=0","--spring.datasource.url="+postgres.getJdbcUrl("postgres","postgres"),
                        "--spring.datasource.username=postgres","--spring.datasource.password=","--spring.jpa.hibernate.ddl-auto=validate")) {
            var jdbc=context.getBean(JdbcTemplate.class);var json=context.getBean(ObjectMapper.class);
            var mvc=MockMvcBuilders.webAppContextSetup((WebApplicationContext)context).apply(springSecurity()).build();
            UUID userId=UUID.randomUUID(),template=UUID.randomUUID(),ingredient=UUID.randomUUID(),component=UUID.randomUUID();
            jdbc.update("insert into users(id,username,password_hash,created_at,enabled) values (?,?,?,now(),true)",userId,userId.toString(),"unused");
            jdbc.update("insert into product_templates(id,name,normalized_name,category,default_unit,common,default_tracking_mode) values (?,?,?,'OTHER','PIECE',false,'QUANTITY')",template,"Egg "+template,"egg "+template);
            UUID process=jdbc.queryForObject("select id from cooking_processes where process_key='MIX_MEATBALL_MIXTURE'",UUID.class);
            var principal=new AuthenticatedUser(userId,userId.toString(),"unused",true);
            String structured="""
                    {"parts":[{"text":"Use "},{"recipeIngredientId":"%s","quantity":0.25,"unit":"PIECE"},{"text":" in "},{"preparedComponentId":"%s"}]}
                    """.formatted(ingredient,component);
            var payload=json.readTree("""
                    {"name":"Dinner","description":"Canonical draft",
                     "ingredients":[{"id":"%s","productTemplateId":"%s","quantity":0.5,"unit":"PIECE","sortOrder":1}],
                     "preparedComponents":[{"id":"%s","key":"MIX","name":"Egg mixture","sortOrder":1,
                       "ingredients":[{"recipeIngredientId":"%s","quantity":0.25,"unit":"PIECE","sortOrder":1}],
                       "preparationSteps":[{"instruction":"Whisk gently","sortOrder":1},{"instruction":"Use egg in mixture","structuredInstruction":%s,"sortOrder":2}]}],
                     "preparationSteps":[{"instruction":"Use egg in mixture","structuredInstruction":%s,"sortOrder":1}],
                     "equipmentRequirements":[{"equipmentType":"PAN","label":"Small pan","sortOrder":1}],
                     "steps":[{"type":"TEXT","instruction":"Use egg in mixture","structuredInstruction":%s,"sortOrder":1},
                       {"type":"PROCESS","cookingProcessId":"%s","sortOrder":2,"parameterBindings":[{"parameterKey":"BASE","preparedComponentId":"%s"},{"parameterKey":"MIX_TIME","durationSeconds":135}]}]}
                    """.formatted(ingredient,template,component,ingredient,structured,structured,structured,process,component));
            var created=json.readTree(mvc.perform(post("/v1/recipes").with(user(principal)).with(csrf()).contentType("application/json").content(payload.toString()))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
            String id=created.get("id").asText();
            var scaled=json.readTree(mvc.perform(get("/v1/recipes/"+id).param("portions","4").with(user(principal))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(scaled.at("/ingredients/0/quantity").decimalValue()).isEqualByComparingTo("2");
            assertThat(scaled.at("/preparedComponents/0/ingredients/0/quantity").decimalValue()).isEqualByComparingTo("1");
            var canonical=json.readTree(mvc.perform(get("/v1/recipes/"+id).param("portions","1").with(user(principal))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(canonical.at("/preparedComponents/0/preparationSteps").size()).isEqualTo(2);
            assertThat(canonical.at("/steps/0/structuredInstruction/parts/1/recipeIngredientId").asText()).isEqualTo(ingredient.toString());
            assertThat(canonical.at("/equipmentRequirements/0/label").asText()).isEqualTo("Small pan");
            mvc.perform(patch("/v1/recipes/"+id).with(user(principal)).with(csrf()).contentType("application/json").content(payload.toString())).andExpect(status().isOk());
            var reopened=json.readTree(mvc.perform(get("/v1/recipes/"+id).param("portions","1").with(user(principal))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(reopened.at("/ingredients/0/quantity").decimalValue()).isEqualByComparingTo("0.5");
            assertThat(reopened.at("/preparedComponents/0/ingredients/0/quantity").decimalValue()).isEqualByComparingTo("0.25");
            assertThat(reopened.at("/preparedComponents/0/preparationSteps/1/structuredInstruction/parts/3/preparedComponentId").asText()).isEqualTo(component.toString());
            assertThat(reopened.at("/steps/1/parameterBindings").toString()).contains("MIX_TIME","135",component.toString());
            var invalid=payload.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)invalid.at("/preparedComponents/0/ingredients/0")).put("quantity",1);
            mvc.perform(patch("/v1/recipes/"+id).with(user(principal)).with(csrf()).contentType("application/json").content(invalid.toString())).andExpect(status().isBadRequest());
            var retained=json.readTree(mvc.perform(get("/v1/recipes/"+id).with(user(principal))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(retained.at("/preparedComponents/0/ingredients/0/quantity").decimalValue()).isEqualByComparingTo("0.25");
        }
    }
}
