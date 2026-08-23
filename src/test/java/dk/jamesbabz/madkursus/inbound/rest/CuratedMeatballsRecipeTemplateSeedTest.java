package dk.jamesbabz.madkursus.inbound.rest;

import java.math.BigDecimal;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CuratedMeatballsRecipeTemplateSeedTest {
    private final ObjectMapper mapper=new ObjectMapper();

    @Test void testedTemplateHasPracticalOnePortionQuantitiesAndValidPartialAllocations()throws Exception {
        JsonNode recipe=read("seed/recipe-template-meatballs-in-tomato-sauce.json").path("recipe");
        assertThat(recipe.path("name").asText()).isEqualTo("Kødboller i tomatsovs med pasta");
        Map<String,JsonNode> ingredients=new LinkedHashMap<>();recipe.path("ingredients").forEach(value->ingredients.put(value.path("key").asText(),value));
        assertThat(ingredients).containsKeys("MEAT","ONION","EGG","FLOUR","MILK","SALT","PEPPER","PAPRIKA","GARLIC","TOMATO_PASTE","TOMATOES","THYME","OIL","PASTA");
        assertThat(ingredients.get("ONION").path("quantity").decimalValue()).isEqualByComparingTo("0.5");
        assertThat(ingredients.get("MEAT").path("quantity").decimalValue().multiply(BigDecimal.TWO)).isEqualByComparingTo("400");
        assertThat(ingredients.get("TOMATOES").path("quantity").decimalValue().multiply(BigDecimal.TWO)).isEqualByComparingTo("400");
        assertThat(ingredients.get("MEAT").hasNonNull("preparation")).isFalse();
        assertThat(ingredients.get("TOMATOES").hasNonNull("preparation")).isFalse();
        assertThat(ingredients.get("PASTA").path("quantity").decimalValue()).isEqualByComparingTo("100");
        assertThat(ingredients.get("MILK").path("unit").asText()).isEqualTo("DECILITER");
        assertThat(ingredients.get("PEPPER").path("unit").asText()).isEqualTo("GRINDER_TURN");
        assertThat(ingredients.get("PAPRIKA").path("unit").asText()).isEqualTo("TEASPOON");

        Map<String,JsonNode> processes=new HashMap<>();read("seed/cooking-processes.json").path("processes").forEach(value->processes.put(value.path("key").asText(),value));
        Map<String,BigDecimal> allocated=new HashMap<>();Set<String> usedProcesses=new HashSet<>();
        for(JsonNode step:recipe.path("steps"))if("PROCESS".equals(step.path("type").asText())){
            String processKey=step.path("process").asText();usedProcesses.add(processKey);JsonNode process=processes.get(processKey);assertThat(process).isNotNull();
            Set<String> parameters=new HashSet<>();process.path("parameters").forEach(value->parameters.add(value.path("key").asText()));
            step.path("bindings").fields().forEachRemaining(field->{assertThat(parameters).contains(field.getKey());JsonNode value=field.getValue();if(value.hasNonNull("ingredient")){String ingredient=value.path("ingredient").asText();assertThat(ingredients).containsKey(ingredient);allocated.merge(ingredient,base(value.path("quantity").decimalValue(),value.path("unit").asText()),BigDecimal::add);}});
        }
        assertThat(usedProcesses).containsExactlyInAnyOrder("MIX_MEATBALL_MIXTURE","PAN_FRY_MEATBALLS","BOIL_PASTA");
        allocated.forEach((key,value)->assertThat(value).as(key).isLessThanOrEqualTo(base(ingredients.get(key).path("quantity").decimalValue(),ingredients.get(key).path("unit").asText())));
        assertThat(allocated.get("ONION")).isEqualByComparingTo("0.25");
        assertThat(allocated.get("SALT")).isEqualByComparingTo("7.5");
        assertThat(allocated.get("PEPPER")).isEqualByComparingTo("5");
        assertThat(recipe.path("preparation")).hasSize(6);
        assertThat(recipe.path("equipment").get(0).path("label").asText()).isEqualTo("Låg til pande");
        String steps=recipe.path("steps").toString();
        assertThat(steps).contains("30 sekunder under omrøring", "1 minut under omrøring");
        assertThat(steps).doesNotContain("400–500", "½ dåse", "Sæt pastavandet over");
        assertThat(recipe.path("steps").findValuesAsText("process")).containsOnlyOnce("BOIL_PASTA");
    }

    @Test void testedBatchScalesIngredientsButKeepsProcessDurationsStatic()throws Exception {
        JsonNode recipe=read("seed/recipe-template-meatballs-in-tomato-sauce.json").path("recipe");Map<String,JsonNode> ingredients=new HashMap<>();recipe.path("ingredients").forEach(value->ingredients.put(value.path("key").asText(),value));
        assertThat(ingredients.get("PASTA").path("quantity").decimalValue().multiply(BigDecimal.TWO)).isEqualByComparingTo("200");
        assertThat(ingredients.get("PAPRIKA").path("quantity").decimalValue().multiply(BigDecimal.TWO)).isEqualByComparingTo("0.5");
        List<Integer> durations=new ArrayList<>();recipe.path("steps").forEach(step->step.path("bindings").fields().forEachRemaining(binding->{if(binding.getValue().has("durationSeconds"))durations.add(binding.getValue().path("durationSeconds").asInt());}));
        assertThat(durations).containsExactlyInAnyOrder(60,720);
    }

    private BigDecimal base(BigDecimal value,String unit){return value.multiply(switch(unit){case "TEASPOON"->BigDecimal.valueOf(5);case "TABLESPOON"->BigDecimal.valueOf(15);case "DECILITER"->BigDecimal.valueOf(100);default->BigDecimal.ONE;});}
    private JsonNode read(String path)throws Exception{try(var stream=getClass().getClassLoader().getResourceAsStream(path)){assertThat(stream).isNotNull();return mapper.readTree(stream);}}
}
