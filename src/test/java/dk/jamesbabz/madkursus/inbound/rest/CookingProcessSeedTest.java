package dk.jamesbabz.madkursus.inbound.rest;

import java.util.*;
import java.util.regex.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CookingProcessSeedTest {
    private static final Pattern PLACEHOLDER=Pattern.compile("\\{([A-Z][A-Z0-9_]*)}");

    @Test void curatedLibraryIsCompleteAndInternallyValid() throws Exception {
        JsonNode root=read(); JsonNode processes=root.get("processes");
        assertThat(processes.size()).isBetween(8,15);
        Set<String> names=new HashSet<>(), keys=new HashSet<>();
        for(JsonNode process:processes) {
            assertThat(names.add(process.get("name").asText())).isTrue(); assertThat(keys.add(process.get("key").asText())).isTrue();
            assertThat(process.get("steps").size()).isPositive(); assertThat(process.get("completionCriteria").asText()).isNotBlank();
            Set<String> parameters=new HashSet<>(); int order=0;
            for(JsonNode parameter:process.get("parameters")){assertThat(parameters.add(parameter.get("key").asText())).isTrue();assertThat(parameter.get("type").asText()).isIn("INGREDIENT_QUANTITY","QUANTITY","DURATION","TEMPERATURE","HEAT_LEVEL","NUMBER","TEXT");order++;}
            assertThat(order).isPositive(); process.get("steps").forEach(step->assertPlaceholders(step.asText(),parameters)); assertPlaceholders(process.get("completionCriteria").asText(),parameters);
            process.get("equipment").forEach(item->{assertThat(item.get(0).asText()).isIn("STOVE","OVEN","POT","PAN","AIR_FRYER","THERMOMETER","MICROWAVE");assertThat(item.get(1).asText()).isIn("REQUIRED","RECOMMENDED");});
        }
        assertThat(names).contains("Kog kartofler","Rør fars","Steg kyllingebryst");
    }
    private void assertPlaceholders(String template,Set<String> keys){Matcher matcher=PLACEHOLDER.matcher(template);while(matcher.find())assertThat(keys).contains(matcher.group(1));assertThat(matcher.replaceAll("")).doesNotContain("{","}");}
    private JsonNode read()throws Exception{try(var stream=getClass().getClassLoader().getResourceAsStream("db/seed/madkursus-cooking-processes-seed.json")){assertThat(stream).isNotNull();return new ObjectMapper().readTree(stream);}}
}
