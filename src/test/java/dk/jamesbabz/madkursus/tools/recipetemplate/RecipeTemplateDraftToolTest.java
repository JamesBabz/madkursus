package dk.jamesbabz.madkursus.tools.recipetemplate;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class RecipeTemplateDraftToolTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    @TempDir Path temp;
    Path project,draft,canonical;

    @BeforeEach void setup()throws Exception {
        project=temp.resolve("project");Path seed=project.resolve("src/main/resources/seed");Files.createDirectories(seed);
        for(String file:List.of("product-templates.json","cooking-processes.json","recipe-templates.json"))Files.copy(Path.of("src/main/resources/seed",file),seed.resolve(file));
        draft=temp.resolve("draft.json");Files.copy(Path.of("docs/examples/recipe-template-draft.json"),draft);canonical=seed.resolve("recipe-templates.json");
    }

    @Test void validDraftReportsResolvedStructureWithoutWriting()throws Exception {String before=Files.readString(canonical);var result=tool().validate(draft);assertThat(result.key()).isEqualTo("DILL_POTATOES_EXAMPLE");assertThat(result.ingredients()).isEqualTo(1);assertThat(result.components()).isEqualTo(1);assertThat(result.processes()).containsExactly("BOIL_POTATOES");assertThat(Files.readString(canonical)).isEqualTo(before);}
    @Test void missingProductTemplateFailsClearly(){mutate(root->obj(root.withArray("ingredients").get(0)).put("productTemplate","DOES_NOT_EXIST"));assertInvalid("Unknown ProductTemplate");}
    @Test void missingCookingProcessFailsClearly(){mutate(root->obj(root.withArray("steps").get(0)).put("process","DOES_NOT_EXIST"));assertInvalid("Unknown CookingProcess");}
    @Test void invalidRecipeUnitFailsClearly(){mutate(root->obj(root.withArray("ingredients").get(0)).put("unit","HANDFUL"));assertInvalid("Unknown RecipeUnit");}
    @Test void componentOverallocationFailsClearly(){mutate(root->obj(obj(root.withArray("preparedComponents").get(0)).withArray("ingredients").get(0)).put("quantity",251));assertInvalid("Allocations exceed");}
    @Test void invalidPreparedComponentReferenceFailsClearly(){mutate(root->obj(root.withArray("steps").get(0)).with("bindings").with("POTATOES").put("component","UNKNOWN"));assertInvalid("unknown PreparedComponent");}
    @Test void missingRequiredProcessInputFailsClearly(){mutate(root->obj(root.withArray("steps").get(0)).set("bindings",JSON.createObjectNode()));assertInvalid("Missing required process input POTATOES");}
    @Test void nonOverrideableDefaultIsRejected(){mutate(root->obj(root.withArray("steps").get(0)).with("bindings").set("BOIL_HEAT",JSON.createObjectNode().put("heatLevel","LOW")));assertInvalid("not overrideable");}
    @Test void invalidStructuredIngredientReferenceIsRejected(){mutate(root->obj(obj(root.withArray("preparation").get(0)).get("instruction")).withArray("parts").add(JSON.createObjectNode().put("ingredient","UNKNOWN")));assertInvalid("unknown ingredient");}
    @Test void invalidStructuredPartAndAmbiguousInstructionAreRejected(){mutate(root->obj(obj(root.withArray("steps").get(1)).get("instruction")).withArray("parts").add(JSON.createObjectNode().put("text","x").put("scaledNumber",2)));assertInvalid("exactly one supported");}
    @Test void partialStructuredReferenceCannotExceedIngredient(){mutate(root->obj(obj(root.withArray("preparation").get(0)).get("instruction")).withArray("parts").add(JSON.createObjectNode().put("ingredient","POTATOES").put("quantity",251).put("unit","GRAM")));assertInvalid("exceeds ingredient quantity");}
    @Test void untrackedWaterIsAcceptedAsARegularScalableDraftIngredient()throws Exception {mutate(root->{ObjectNode water=JSON.createObjectNode().put("key","VAND_TIL_SOVS").put("productTemplate","VAND").put("quantity",0.5).put("unit","DECILITER");root.withArray("ingredients").add(water);});assertThat(tool().validate(draft).ingredients()).isEqualTo(2);}

    @Test void combinedWorkflowTreatsUnknownKeyAsAddAndGeneratesAddCandidate()throws Exception {String before=Files.readString(canonical);Path migrations=temp.resolve("migrations");var result=tool().processDraft(draft,false,migrations);assertThat(result.action()).isEqualTo(RecipeTemplateDraftTool.Action.ADD);assertThat(result.migration().getFileName().toString()).isEqualTo("V_NEXT__add_dill_potatoes_example.sql");assertThat(Files.readString(canonical)).isNotEqualTo(before);assertThat(result.migration()).exists();}
    @Test void combinedWorkflowTreatsExistingKeyAsUpdateAndPreservesTemplateId()throws Exception {ObjectNode existing=canonicalRecipe("MEAT_SAUCE_WITH_PASTA");String originalName=existing.path("name").asText(),expectedId=UUID.nameUUIDFromBytes(("recipe-template:"+originalName.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8)).toString();existing.put("description","Opdateret beskrivelse");write(existing);var result=tool().processDraft(draft,false,temp.resolve("migrations"));assertThat(result.action()).isEqualTo(RecipeTemplateDraftTool.Action.UPDATE);assertThat(result.migration().getFileName().toString()).isEqualTo("V_NEXT__update_meat_sauce_with_pasta.sql");assertThat(Files.readString(result.migration())).contains("VALUES ('"+expectedId+"'");assertThat(canonicalRecipe("MEAT_SAUCE_WITH_PASTA").path("id").asText()).isEqualTo(expectedId);}
    @Test void similarNameWithDifferentKeyWarnsButNeverAutoUpdates()throws Exception {mutate(root->root.put("name","Frikadeller med kartofler"));String before=Files.readString(canonical);var result=tool().processDraft(draft,true,temp.resolve("migrations"));assertThat(result.action()).isEqualTo(RecipeTemplateDraftTool.Action.ADD);assertThat(result.warnings()).singleElement().asString().contains("DANISH_MEATBALLS","No automatic update");assertThat(Files.readString(canonical)).isEqualTo(before);}
    @Test void combinedWorkflowInvalidDraftIsAtomicEvenWhenCandidateAlreadyExists()throws Exception {Path migrations=temp.resolve("migrations"),candidate=migrations.resolve("V_NEXT__add_dill_potatoes_example.sql");Files.createDirectories(migrations);Files.writeString(candidate,"keep");String before=Files.readString(canonical);mutate(root->obj(root.withArray("ingredients").get(0)).put("productTemplate","UNKNOWN"));assertThatThrownBy(()->tool().processDraft(draft,false,migrations)).hasMessageContaining("Unknown ProductTemplate");assertThat(Files.readString(canonical)).isEqualTo(before);assertThat(Files.readString(candidate)).isEqualTo("keep");}
    @Test void combinedWorkflowDryRunReportsPlanWithoutChangingFiles()throws Exception {String before=Files.readString(canonical);Path migrations=temp.resolve("migrations");var result=tool().processDraft(draft,true,migrations);assertThat(result.action()).isEqualTo(RecipeTemplateDraftTool.Action.ADD);assertThat(result.dryRun()).isTrue();assertThat(result.migration().getFileName().toString()).startsWith("V_NEXT__add_");assertThat(Files.readString(canonical)).isEqualTo(before);assertThat(migrations).doesNotExist();}
    @Test void explicitStableTemplateIdIsTheParentAndStableKeyNamespacesDeterministicChildIds()throws Exception {ObjectNode existing=canonicalRecipe("KOEDBOLLER_I_TOMATSOVS_MED_PASTA");String id=existing.path("id").asText(),stepId=UUID.nameUUIDFromBytes("recipe-template-child:KOEDBOLLER_I_TOMATSOVS_MED_PASTA:step:1".getBytes(StandardCharsets.UTF_8)).toString();write(existing);var result=tool().processDraft(draft,false,temp.resolve("migrations"));assertThat(Files.readString(result.migration())).contains("VALUES ('"+id+"'","INSERT INTO recipe_template_steps(id,recipe_template_id","'"+stepId+"','"+id+"'");}
    @Test void recoveryRegeneratesAddForExistingCanonicalTemplateWithoutChangingCanonical()throws Exception {Files.copy(Path.of("src/main/resources/recipe-templates/koedboller-i-tomatsovs-med-pasta.json"),draft,StandardCopyOption.REPLACE_EXISTING);String before=Files.readString(canonical);Path migrations=temp.resolve("migrations");var first=tool().regenerateAddMigration(draft,migrations);String sql=Files.readString(first.migration());assertThat(first.action()).isEqualTo(RecipeTemplateDraftTool.Action.ADD);assertThat(first.migration().getFileName().toString()).isEqualTo("V_NEXT__add_koedboller_i_tomatsovs_med_pasta.sql");assertThat(sql).contains("INSERT INTO recipe_templates","DELETE FROM recipe_template_steps");String oilProductId=canonicalProductId("NEUTRAL_OLIE");assertThat(sql).contains("'FAT','18943e10-2294-31d5-8612-e64d1f7864f5','"+oilProductId+"',NULL,0.5,'TABLESPOON'");assertThat(Files.readString(canonical)).isEqualTo(before);Set<String> insertedIds=new HashSet<>();var matcher=java.util.regex.Pattern.compile("(?:VALUES \\('|SELECT ')([0-9a-f-]{36})'").matcher(sql);while(matcher.find())assertThat(insertedIds.add(matcher.group(1))).as("unique generated primary ID %s",matcher.group(1)).isTrue();assertThat(insertedIds).hasSizeGreaterThan(20);var second=new RecipeTemplateDraftTool(project).regenerateAddMigration(draft,migrations);assertThat(Files.readString(second.migration())).isEqualTo(sql);}
    @Test void recoveryRequiresExistingKeyAndCanonicalDraftEquality()throws Exception {String before=Files.readString(canonical);assertThatThrownBy(()->tool().regenerateAddMigration(draft,temp.resolve("migrations"))).hasMessageContaining("existing canonical");assertThat(Files.readString(canonical)).isEqualTo(before);Files.copy(Path.of("src/main/resources/recipe-templates/koedboller-i-tomatsovs-med-pasta.json"),draft,StandardCopyOption.REPLACE_EXISTING);mutate(root->root.put("description","not canonical"));assertThatThrownBy(()->tool().regenerateAddMigration(draft,temp.resolve("migrations"))).hasMessageContaining("differs from the canonical");assertThat(Files.readString(canonical)).isEqualTo(before);}

    @Test void existingKeyRequiresExplicitUpdateAndInvalidImportWritesNothing()throws Exception {
        ObjectNode root=read();root.put("key","DANISH_MEATBALLS");write(root);String before=Files.readString(canonical);
        assertThatThrownBy(()->tool().importDraft(draft,false,temp.resolve("migrations"))).hasMessageContaining("--update");
        assertThat(Files.readString(canonical)).isEqualTo(before);assertThat(temp.resolve("migrations")).doesNotExist();
    }

    @Test void updatePreservesStableTemplateIdAndMigrationIsDeterministic()throws Exception {
        JsonNode canonicalRoot=JSON.readTree(canonical.toFile());ObjectNode existing=null;for(JsonNode value:canonicalRoot.path("recipes"))if(value.path("key").asText().equals("MEAT_SAUCE_WITH_PASTA"))existing=(ObjectNode)value.deepCopy();assertThat(existing).isNotNull();String originalName=existing.path("name").asText();existing.put("name","Kødsovs med nyt reviewnavn");write(existing);Path migrations=temp.resolve("migrations");var first=tool().importDraft(draft,true,migrations);String sql1=Files.readString(first.migration());var second=new RecipeTemplateDraftTool(project).importDraft(draft,true,migrations);String sql2=Files.readString(second.migration());UUID expected=UUID.nameUUIDFromBytes(("recipe-template:"+originalName.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));assertThat(sql1).contains(expected.toString()).isEqualTo(sql2);assertThat(sql1).doesNotContain("DELETE FROM recipes","UPDATE recipes","source_template_id");
    }

    @Test void importChangesOnlyTargetRecipeAndProducesReviewCandidate()throws Exception {
        JsonNode before=JSON.readTree(canonical.toFile());var result=tool().importDraft(draft,false,temp.resolve("migrations"));JsonNode after=JSON.readTree(canonical.toFile());assertThat(after.path("recipes").size()).isEqualTo(before.path("recipes").size()+1);assertThat(result.migration().getFileName().toString()).startsWith("V_NEXT__add_");assertThat(Files.readString(result.migration())).contains("BEGIN;","recipe_template_prepared_components","recipe_template_process_bindings","COMMIT;");
    }

    @Test void curatedMeatballTemplateRoundTripsThroughValidator()throws Exception {
        Path source=Path.of("src/main/resources/seed/recipe-template-meatballs-in-tomato-sauce.json");var result=tool().validate(source);assertThat(result.name()).isEqualTo("Kødboller i tomatsovs med pasta");assertThat(result.processes()).contains("MIX_MEATBALL_MIXTURE","PAN_FRY_MEATBALLS","BOIL_PASTA");
    }

    private RecipeTemplateDraftTool tool(){try{return new RecipeTemplateDraftTool(project);}catch(Exception e){throw new RuntimeException(e);}}
    private ObjectNode read(){try{return (ObjectNode)JSON.readTree(draft.toFile());}catch(Exception e){throw new RuntimeException(e);}}
    private ObjectNode canonicalRecipe(String key){try{for(JsonNode value:JSON.readTree(canonical.toFile()).path("recipes"))if(key.equals(value.path("key").asText()))return (ObjectNode)value.deepCopy();throw new AssertionError("Missing "+key);}catch(Exception e){throw new RuntimeException(e);}}
    private String canonicalProductId(String key){try{for(JsonNode value:JSON.readTree(project.resolve("src/main/resources/seed/product-templates.json").toFile()).path("products"))if(key.equals(value.path("key").asText()))return value.path("id").asText();throw new AssertionError("Missing "+key);}catch(Exception e){throw new RuntimeException(e);}}
    private void write(ObjectNode value){try{JSON.writerWithDefaultPrettyPrinter().writeValue(draft.toFile(),value);}catch(Exception e){throw new RuntimeException(e);}}
    private void mutate(java.util.function.Consumer<ObjectNode> change){ObjectNode value=read();change.accept(value);write(value);}
    private static ObjectNode obj(JsonNode value){return (ObjectNode)value;}
    private void assertInvalid(String message){String before;try{before=Files.readString(canonical);}catch(Exception e){throw new RuntimeException(e);}assertThatThrownBy(()->tool().importDraft(draft,false,temp.resolve("migrations"))).hasMessageContaining(message);try{assertThat(Files.readString(canonical)).isEqualTo(before);}catch(Exception e){throw new RuntimeException(e);}assertThat(temp.resolve("migrations")).doesNotExist();}
}
