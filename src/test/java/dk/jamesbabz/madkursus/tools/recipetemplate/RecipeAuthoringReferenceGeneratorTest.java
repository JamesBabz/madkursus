package dk.jamesbabz.madkursus.tools.recipetemplate;

import com.fasterxml.jackson.databind.*;
import dk.jamesbabz.madkursus.service.models.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class RecipeAuthoringReferenceGeneratorTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    @TempDir Path temp;

    @Test void generationIsDeterministicCompleteAndContainsNoRuntimeData()throws Exception {
        var generator=new RecipeAuthoringReferenceGenerator(Path.of("."));Path output=temp.resolve("reference.json");generator.generate(output);byte[] first=Files.readAllBytes(output);generator.generate(output);assertThat(Files.readAllBytes(output)).isEqualTo(first);
        JsonNode reference=JSON.readTree(output.toFile()),productSource=JSON.readTree(Path.of("src/main/resources/seed/product-templates.json").toFile()),processSource=JSON.readTree(Path.of("src/main/resources/seed/cooking-processes.json").toFile());
        assertThat(keys(reference.path("productTemplates"))).containsExactlyInAnyOrderElementsOf(keys(productSource.path("products")));
        assertThat(keys(reference.path("cookingProcesses"))).containsExactlyInAnyOrderElementsOf(keys(processSource.path("processes")));
        assertThat(keys(reference.path("recipeUnits"))).containsExactlyInAnyOrder(Arrays.stream(RecipeUnit.values()).map(Enum::name).toArray(String[]::new));
        assertThat(keys(reference.path("equipmentTypes"))).containsExactlyInAnyOrder(Arrays.stream(EquipmentType.values()).map(Enum::name).toArray(String[]::new));
        String text=Files.readString(output).toLowerCase(Locale.ROOT);assertThat(text).doesNotContain("password","username","jdbc:","inventory","mealplan","mealplans","credential","user data");
    }

    @Test void processReferenceExplainsRequiredInputsDerivedRulesAndOverrides()throws Exception {
        JsonNode reference=generate(),boil=find(reference.path("cookingProcesses"),"BOIL_POTATOES");
        JsonNode potatoes=find(boil.path("requiredInputs"),"POTATOES");assertThat(potatoes.path("required").asBoolean()).isTrue();assertThat(strings(potatoes.path("accepts"))).containsExactly("ingredient","preparedComponent");
        assertThat(keys(boil.path("derivedValues"))).contains("WATER","SALT");assertThat(keys(boil.path("overrideableValues"))).contains("WATER","SALT","SIMMER_TIME");
        assertThat(find(boil.path("derivedValues"),"WATER").path("rule").asText()).isEqualTo("POTATO_WATER_PER_GRAM");
    }

    @Test void referenceContainsProductAuthoringFieldsAndDraftContractForCanonicalExample()throws Exception {
        JsonNode reference=generate(),product=find(reference.path("productTemplates"),"KARTOFFEL"),contract=reference.path("draftContract");
        assertThat(product.path("name").asText()).isEqualTo("Kartoffel");assertThat(product.path("defaultUnit").asText()).isNotBlank();assertThat(product.path("trackingMode").asText()).isNotBlank();assertThat(product.path("aliases").isArray()).isTrue();
        assertThat(strings(contract.path("requiredTopLevel"))).contains("key","name","ingredients","steps");assertThat(contract.path("shapes").has("preparedComponent")).isTrue();assertThat(contract.path("shapes").has("processStep")).isTrue();assertThat(contract.path("shapes").has("binding")).isTrue();
        JsonNode example=JSON.readTree(Path.of("docs/examples/recipe-template-draft.json").toFile());assertThat(keys(reference.path("productTemplates"))).contains(example.path("ingredients").get(0).path("productTemplate").asText());assertThat(keys(reference.path("cookingProcesses"))).contains(example.path("steps").get(0).path("process").asText());assertThat(keys(reference.path("recipeUnits"))).contains(example.path("ingredients").get(0).path("unit").asText());
        assertThat(new RecipeTemplateDraftTool(Path.of(".")).validate(Path.of("docs/examples/recipe-template-draft.json")).key()).isEqualTo("DILL_POTATOES_EXAMPLE");
    }

    @Test void referencePublishesUntrackedWaterForScalableRecipeAuthoring()throws Exception {JsonNode water=find(generate().path("productTemplates"),"VAND");assertThat(water.path("name").asText()).isEqualTo("Vand");assertThat(water.path("defaultUnit").asText()).isEqualTo("MILLILITER");assertThat(water.path("trackingMode").asText()).isEqualTo("UNTRACKED");}

    private JsonNode generate()throws Exception{Path output=temp.resolve(UUID.randomUUID()+".json");new RecipeAuthoringReferenceGenerator(Path.of(".")).generate(output);return JSON.readTree(output.toFile());}
    private Set<String> keys(JsonNode values){Set<String> result=new LinkedHashSet<>();values.forEach(v->result.add(v.path("key").asText()));return result;}
    private List<String> strings(JsonNode values){List<String> result=new ArrayList<>();values.forEach(v->result.add(v.asText()));return result;}
    private JsonNode find(JsonNode values,String key){for(JsonNode value:values)if(value.path("key").asText().equals(key))return value;throw new AssertionError("Missing key "+key);}
}
