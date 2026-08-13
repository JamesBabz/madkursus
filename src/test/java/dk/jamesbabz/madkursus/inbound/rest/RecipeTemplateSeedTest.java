package dk.jamesbabz.madkursus.inbound.rest;

import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RecipeTemplateSeedTest {
    @Test void everySeededRecipeIsCompleteAndReferencesAnExistingProductTemplate() throws Exception {
        ObjectMapper mapper=new ObjectMapper(); JsonNode products=read(mapper,"db/seed/madkursus-product-templates-seed.json").get("products");
        Set<String> productNames=new HashSet<>(); products.forEach(product->productNames.add(normalize(product.get("name").asText()))); productNames.add("pølser");
        JsonNode recipes=read(mapper,"db/seed/madkursus-recipe-templates-seed.json").get("recipes"); Set<String> recipeNames=new HashSet<>();
        assertThat(recipes.size()).isBetween(15,25);
        for(JsonNode recipe:recipes){assertThat(recipeNames.add(normalize(recipe.get("name").asText()))).isTrue();assertThat(recipe.get("description").asText()).isNotBlank();assertThat(recipe.get("ingredients").size()).isPositive();assertThat(recipe.get("steps").size()).isPositive();for(JsonNode ingredient:recipe.get("ingredients")){assertThat(productNames).contains(normalize(ingredient.get(0).asText()));assertThat(ingredient.get(1).decimalValue()).isPositive();assertThat(ingredient.get(2).asText()).isIn("GRAM","MILLILITER","PIECE","TEASPOON","TABLESPOON","DECILITER");}recipe.get("steps").forEach(step->assertThat(step.asText()).isNotBlank());}
    }
    private JsonNode read(ObjectMapper mapper,String path)throws Exception{try(var stream=getClass().getClassLoader().getResourceAsStream(path)){assertThat(stream).isNotNull();return mapper.readTree(stream);}}
    private String normalize(String value){return value.trim().toLowerCase(Locale.ROOT);}
}
