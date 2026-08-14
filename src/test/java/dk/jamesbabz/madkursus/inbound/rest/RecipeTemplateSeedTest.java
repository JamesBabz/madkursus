package dk.jamesbabz.madkursus.inbound.rest;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeTemplateSeedTest {
    @Test
    void canonicalSourceContainsAllFifteenValidTemplatesAndResolvableReferences() throws Exception {
        ObjectMapper mapper=new ObjectMapper();
        Set<String> productNames=new HashSet<>();
        read(mapper,"db/seed/madkursus-product-templates-seed.json").get("products")
                .forEach(product->productNames.add(normalize(product.get("name").asText())));
        productNames.add("pølser");
        Map<String,JsonNode> processes=new HashMap<>();
        read(mapper,"db/seed/madkursus-cooking-processes-seed.json").get("processes")
                .forEach(process->processes.put(process.get("key").asText(),process));

        JsonNode recipes=read(mapper,"seed/recipe-templates.json").get("recipes");
        Set<String> recipeNames=new HashSet<>(),recipeKeys=new HashSet<>();
        assertThat(recipes).hasSize(15);
        for(JsonNode recipe:recipes){
            assertThat(recipeNames.add(normalize(recipe.get("name").asText()))).isTrue();
            assertThat(recipeKeys.add(recipe.get("key").asText())).isTrue();
            assertThat(recipe.get("description").asText()).isNotBlank();
            Map<String,JsonNode> ingredients=new HashMap<>();
            for(JsonNode ingredient:recipe.get("ingredients")){
                assertThat(ingredients.put(ingredient.get("key").asText(),ingredient)).isNull();
                assertThat(productNames).contains(normalize(ingredient.get("productTemplate").asText()));
                assertThat(ingredient.get("quantity").decimalValue()).isPositive();
            }
            assertThat(recipe.get("steps")).isNotEmpty();
            Map<String,BigDecimal> allocated=new HashMap<>();
            for(JsonNode step:recipe.get("steps")){
                if("TEXT".equals(step.get("type").asText())) { assertThat(step.get("instruction").asText()).isNotBlank(); continue; }
                JsonNode process=processes.get(step.get("process").asText());
                assertThat(process).as("process %s",step.get("process")).isNotNull();
                Map<String,JsonNode> parameters=new HashMap<>(); process.get("parameters").forEach(p->parameters.put(p.get("key").asText(),p));
                step.get("bindings").fields().forEachRemaining(binding->{
                    assertThat(parameters).containsKey(binding.getKey());
                    JsonNode value=binding.getValue();
                    if(value.has("ingredient")){
                        String ingredientKey=value.get("ingredient").asText();
                        assertThat(ingredients).containsKey(ingredientKey);
                        allocated.merge(ingredientKey,value.get("quantity").decimalValue(),BigDecimal::add);
                    }
                });
                parameters.forEach((key,parameter)->{
                    boolean hasDefault=parameter.has("default");
                    if(parameter.get("required").asBoolean()&&!hasDefault)assertThat(step.get("bindings").has(key)).as("required %s",key).isTrue();
                });
            }
            allocated.forEach((key,quantity)->assertThat(quantity).isLessThanOrEqualTo(ingredients.get(key).get("quantity").decimalValue()));
        }
        assertThat(recipeNames).contains("frikadeller","kødsovs med pasta","kylling med ris og grøntsager","millionbøf");
    }

    @Test
    void legacyV15SourceRemainsReadableForFreshDatabases() throws Exception {
        JsonNode recipes=read(new ObjectMapper(),"db/seed/madkursus-recipe-templates-seed.json").get("recipes");
        assertThat(recipes).hasSize(15);
        recipes.forEach(recipe->recipe.get("steps").forEach(step->assertThat(step.isTextual()).isTrue()));
    }

    private JsonNode read(ObjectMapper mapper,String path)throws Exception{try(var stream=getClass().getClassLoader().getResourceAsStream(path)){assertThat(stream).isNotNull();return mapper.readTree(stream);}}
    private String normalize(String value){return value.trim().toLowerCase(Locale.ROOT);}
}
