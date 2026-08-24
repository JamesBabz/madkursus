package dk.jamesbabz.madkursus.inbound.rest;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dk.jamesbabz.madkursus.tools.recipetemplate.RecipeTemplateDraftTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeTemplateSeedTest {
    @TempDir Path temp;

    @Test
    void canonicalSourceContainsUniqueBaselineAndExternallyAuthoredTemplatesThatAllValidate() throws Exception {
        ObjectMapper mapper=new ObjectMapper();
        JsonNode recipes=read(mapper,"seed/recipe-templates.json").get("recipes");
        Set<String> recipeNames=new HashSet<>(),recipeKeys=new HashSet<>(),recipeIds=new HashSet<>();
        Set<String> productReferences=new HashSet<>();read(mapper,"seed/product-templates.json").path("products").forEach(product->{productReferences.add(product.path("key").asText());productReferences.add(product.path("name").asText());});
        Set<String> processKeys=new HashSet<>();read(mapper,"seed/cooking-processes.json").path("processes").forEach(process->processKeys.add(process.path("key").asText()));
        RecipeTemplateDraftTool validator=new RecipeTemplateDraftTool(Path.of("."));
        for(JsonNode recipe:recipes){
            assertThat(recipeNames.add(normalize(recipe.get("name").asText()))).isTrue();
            assertThat(recipeKeys.add(recipe.get("key").asText())).isTrue();
            String id=recipe.hasNonNull("id")?recipe.get("id").asText():UUID.nameUUIDFromBytes(("recipe-template:"+normalize(recipe.get("name").asText())).getBytes(StandardCharsets.UTF_8)).toString();assertThat(recipeIds.add(id)).isTrue();
            Map<String,JsonNode> ingredients=new HashMap<>();for(JsonNode ingredient:recipe.path("ingredients")){assertThat(ingredients.put(ingredient.path("key").asText(),ingredient)).isNull();assertThat(productReferences).contains(ingredient.path("productTemplate").asText());assertThat(ingredient.path("quantity").decimalValue()).isPositive();}
            Set<String> components=new HashSet<>();for(JsonNode component:recipe.path("preparedComponents")){assertThat(components.add(component.path("key").asText())).isTrue();component.path("ingredients").forEach(allocation->assertThat(ingredients).containsKey(allocation.path("ingredient").asText()));}
            assertThat(recipe.path("steps")).isNotEmpty();for(JsonNode step:recipe.path("steps")){if("PROCESS".equals(step.path("type").asText())){assertThat(processKeys).contains(step.path("process").asText());step.path("bindings").forEach(binding->{if(binding.hasNonNull("ingredient"))assertThat(ingredients).containsKey(binding.path("ingredient").asText());if(binding.hasNonNull("component"))assertThat(components).contains(binding.path("component").asText());});}else{JsonNode instruction=step.path("instruction");assertThat(instruction.isTextual()?!instruction.asText().isBlank():!instruction.path("parts").isEmpty()).isTrue();}}
            if("KOEDBOLLER_I_TOMATSOVS_MED_PASTA".equals(recipe.path("key").asText())){Path draft=temp.resolve("imported.json");mapper.writeValue(draft.toFile(),recipe);assertThat(validator.validate(draft).key()).isEqualTo("KOEDBOLLER_I_TOMATSOVS_MED_PASTA");}
        }
        assertThat(recipeKeys).contains("DANISH_MEATBALLS","MEAT_SAUCE_WITH_PASTA","CHICKEN_RICE_VEGETABLES","MILLION_BEEF","KOEDBOLLER_I_TOMATSOVS_MED_PASTA");
        assertThat(recipeNames).contains("frikadeller","kødsovs med pasta","kylling med ris og grøntsager","millionbøf","kødboller i tomatsovs med pasta");
    }

    @Test
    void legacyV15SourceRemainsReadableForFreshDatabases() throws Exception {
        JsonNode recipes=read(new ObjectMapper(),"db/seed/madkursus-recipe-templates-seed.json").get("recipes");
        assertThat(recipes).hasSize(15);
        recipes.forEach(recipe->recipe.get("steps").forEach(step->assertThat(step.isTextual()).isTrue()));
    }

    @Test
    void v21UsesTheExactImmutableFifteenTemplateBlobFromItsIntroducingCommit() throws Exception {
        byte[] bytes;try(var stream=getClass().getClassLoader().getResourceAsStream("db/migration/data/V21__recipe_templates.json")){assertThat(stream).isNotNull();bytes=stream.readAllBytes();}
        JsonNode recipes=new ObjectMapper().readTree(bytes).path("recipes");
        assertThat(recipes).hasSize(15);
        assertThat(recipes).noneMatch(recipe->"KOEDBOLLER_I_TOMATSOVS_MED_PASTA".equals(recipe.path("key").asText()));
        assertThat(java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))).isEqualTo("b12751e980483fc72eb5f0384ddc3d7565d50492044e3b8dc1609e3f83bbf928");
    }

    private JsonNode read(ObjectMapper mapper,String path)throws Exception{try(var stream=getClass().getClassLoader().getResourceAsStream(path)){assertThat(stream).isNotNull();return mapper.readTree(stream);}}
    private String normalize(String value){return value.trim().toLowerCase(Locale.ROOT);}
}
