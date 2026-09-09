package dk.jamesbabz.madkursus.inbound.rest;

import dk.jamesbabz.madkursus.inbound.rest.recipeimport.RecipeTemplateImportApiDelegateImpl;
import dk.jamesbabz.madkursus.inbound.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value=RecipeTemplateImportApiController.class,properties="madkursus.recipe-template-import.enabled=true")
@Import({SecurityConfig.class,RecipeTemplateImportApiDelegateImpl.class})
class RecipeTemplateImportApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserDetailsService users;
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON=new com.fasterxml.jackson.databind.ObjectMapper();
    @Test void browserValidatesThroughProductionMvcDelegate()throws Exception {
        String node=System.getenv("RECIPE_IMPORT_TEST_NODE");
        org.junit.jupiter.api.Assumptions.assumeTrue(node!=null,"Set RECIPE_IMPORT_TEST_NODE to run the browser/API integration");
        var bridge=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        bridge.createContext("/validate",exchange->{
            try {
                String body=new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                var response=validate(body).andReturn().getResponse();
                byte[] bytes=response.getContentAsByteArray();exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(response.getStatus(),bytes.length);exchange.getResponseBody().write(bytes);
            } catch(Exception failure) {exchange.sendResponseHeaders(500,-1);}
            finally {exchange.close();}
        });
        bridge.start();
        try {
            var builder=new ProcessBuilder(node,"--test","src/test/js/recipe-import-api-browser.test.cjs").redirectErrorStream(true)
                .redirectOutput(Path.of("build/recipe-import-api-browser.log").toFile());
            builder.environment().put("RECIPE_IMPORT_TEST_API","http://127.0.0.1:"+bridge.getAddress().getPort()+"/validate");
            var process=builder.start();
            try {
                org.assertj.core.api.Assertions.assertThat(process.waitFor(60,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                org.assertj.core.api.Assertions.assertThat(process.exitValue()).as(Files.readString(Path.of("build/recipe-import-api-browser.log"))).isZero();
            } finally {if(process.isAlive())process.destroyForcibly();}
        } finally {bridge.stop(0);}
    }
    private com.fasterxml.jackson.databind.node.ObjectNode representative()throws Exception {
        return (com.fasterxml.jackson.databind.node.ObjectNode)JSON.readTree(Files.readString(Path.of("src/test/resources/recipe-import/representative-draft.json")));
    }
    private org.springframework.test.web.servlet.ResultActions validate(String draft)throws Exception {
        return mvc.perform(post("/v1/admin/recipe-template-import/validate").with(user("admin").roles("ADMIN")).with(csrf()).contentType("text/plain").content(draft)).andExpect(status().isOk());
    }
    @Test void representativeCookingPatternsValidateThroughEndpointAndCliService()throws Exception {
        var draft=representative();
        validate(draft.toString()).andExpect(jsonPath("$.valid").value(true)).andExpect(jsonPath("$.errors").isEmpty());
        var tool=new dk.jamesbabz.madkursus.tools.recipetemplate.RecipeTemplateDraftTool(Path.of("."));
        org.assertj.core.api.Assertions.assertThat(tool.prepare(draft.toString()).sql()).contains("0.125", "recipeIngredientId");
        org.assertj.core.api.Assertions.assertThat(tool.processDraft(Path.of("src/test/resources/recipe-import/representative-draft.json"),true,null).dryRun()).isTrue();
        var binding=(com.fasterxml.jackson.databind.node.ObjectNode)draft.path("steps").get(0).path("bindings").path("PASTA");
        binding.remove("quantity");binding.remove("unit");
        validate(draft.toString()).andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.errors[0].path").value("steps[0].bindings.PASTA.quantity"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"missing","null","0","-1"})
    void requiredTopLevelQuantityHasPath(String value)throws Exception {
        var draft=representative();var ingredient=(com.fasterxml.jackson.databind.node.ObjectNode)draft.path("ingredients").get(1);
        if(value.equals("missing"))ingredient.remove("quantity");else ingredient.set("quantity",JSON.readTree(value));
        validate(draft.toString()).andExpect(jsonPath("$.valid").value(false)).andExpect(jsonPath("$.errors[0].path").value("ingredients[1].quantity"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"0","-0.125"})
    void invalidPartialQuantityHasNestedPath(String value)throws Exception {
        var draft=representative();((com.fasterxml.jackson.databind.node.ObjectNode)draft.path("steps").get(1).path("instruction").path("parts").get(2)).set("quantity",JSON.readTree(value));
        validate(draft.toString()).andExpect(jsonPath("$.valid").value(false)).andExpect(jsonPath("$.errors[0].path").value("steps[1].instruction.parts[2].quantity"));
    }
    @Test void independentErrorsAreCollectedAndWrapperPathsArePreserved()throws Exception {
        var draft=representative();((com.fasterxml.jackson.databind.node.ObjectNode)draft.path("ingredients").get(0)).put("productTemplate","UNKNOWN_PRODUCT_REGRESSION");
        ((com.fasterxml.jackson.databind.node.ObjectNode)draft.path("steps").get(1).path("instruction").path("parts").get(2)).put("quantity",0);
        validate(draft.toString()).andExpect(jsonPath("$.errors.length()").value(2))
            .andExpect(jsonPath("$.errors[0].path").value("ingredients[0].productTemplate"))
            .andExpect(jsonPath("$.errors[1].path").value("steps[1].instruction.parts[2].quantity"));
        validate(JSON.createObjectNode().set("recipe",draft).toString()).andExpect(jsonPath("$.errors[1].path").value("recipe.steps[1].instruction.parts[2].quantity"));
    }
    @Test void allocationOverflowIdentifiesAllocationQuantity()throws Exception {
        var draft=representative();var component=draft.putArray("preparedComponents").addObject().put("key","MIX").put("name","Mix");
        component.putArray("ingredients").addObject().put("ingredient","SALT").put("quantity",1).put("unit","TEASPOON");
        validate(draft.toString()).andExpect(jsonPath("$.valid").value(false)).andExpect(jsonPath("$.errors[0].path").value("preparedComponents[0].ingredients[0].quantity"));
    }
    @Test void malformedJsonIncludesParserLineAndColumn()throws Exception {
        validate("{\n  \"key\": !\n}").andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.errors[0].path").value("$"))
            .andExpect(jsonPath("$.errors[0].line").value(2))
            .andExpect(jsonPath("$.errors[0].column").isNumber())
            .andExpect(jsonPath("$.errors[0].message").value(org.hamcrest.Matchers.containsString("Invalid JSON at line 2, column")));
    }
    @Test void runtimeBindingSemanticFailuresBecomePathErrorsRatherThanEscapingTheImporter()throws Exception {
        var draft=representative();
        ((com.fasterxml.jackson.databind.node.ObjectNode)draft.path("steps").get(0).path("bindings"))
            .putObject("COOK_TIME").put("durationSeconds",0);
        validate(draft.toString()).andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.errors[0].path").value("steps[0].bindings.COOK_TIME.durationSeconds"));
    }
    @Test void canonicalRecipesRetainValidationWithKnownLegacyOverallocationRejected()throws Exception {
        var rejected=new java.util.ArrayList<String>();
        for(var recipe:JSON.readTree(Files.readString(Path.of("src/main/resources/seed/recipe-templates.json"))).path("recipes")) {
            var response=validate(recipe.toString()).andReturn().getResponse().getContentAsString();
            if(!JSON.readTree(response).path("valid").asBoolean())rejected.add(recipe.path("key").asText());
        }
        // Existing seed double-allocates meat to both its component and a direct process binding.
        // Keep rejecting that invalid graph instead of weakening allocation checks.
        org.assertj.core.api.Assertions.assertThat(rejected).containsExactly("DANISH_MEATBALLS");
    }
    @Test void reproduceOmittedProcessQuantityThroughRealEndpoint()throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var draft=json.readTree(Files.readString(Path.of("docs/examples/recipe-template-draft.json")));
        var binding=(com.fasterxml.jackson.databind.node.ObjectNode)draft.path("steps").get(0).path("bindings").path("POTATOES");
        binding.remove("component");binding.put("ingredient","POTATOES");
        mvc.perform(post("/v1/admin/recipe-template-import/validate").with(user("admin").roles("ADMIN")).with(csrf()).contentType("text/plain").content(draft.toString()))
            .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
            .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.errors[0].path").value("steps[0].bindings.POTATOES.quantity"));
    }
    @Test void adminCanValidateRawJsonWithCsrf()throws Exception {
        mvc.perform(post("/v1/admin/recipe-template-import/validate").with(user("admin").roles("ADMIN")).with(csrf()).contentType("text/plain").content(Files.readString(Path.of("docs/examples/recipe-template-draft.json"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true)).andExpect(jsonPath("$.action").value("ADD")).andExpect(jsonPath("$.imported").value(false));
    }
    @Test void nonAdminAndMissingCsrfAreDenied()throws Exception {
        for(String action:new String[]{"validate","import"}) {
            mvc.perform(post("/v1/admin/recipe-template-import/"+action).with(user("cook")).with(csrf()).contentType("text/plain").content("{}")).andExpect(status().isForbidden());
            mvc.perform(post("/v1/admin/recipe-template-import/"+action).with(user("admin").roles("ADMIN")).contentType("text/plain").content("{}")).andExpect(status().isForbidden());
        }
        mvc.perform(get("/v1/admin/recipe-template-import").with(user("cook"))).andExpect(status().isForbidden());
    }
    @Test void invalidJsonHasReadableErrors()throws Exception {
        mvc.perform(post("/v1/admin/recipe-template-import/validate").with(user("admin").roles("ADMIN")).with(csrf()).contentType("text/plain").content("{"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(false)).andExpect(jsonPath("$.errors[0]").isNotEmpty());
    }
}
