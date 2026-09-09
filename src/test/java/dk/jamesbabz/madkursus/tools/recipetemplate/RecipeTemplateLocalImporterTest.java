package dk.jamesbabz.madkursus.tools.recipetemplate;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class RecipeTemplateLocalImporterTest {
    @TempDir Path root;
    Path canonical,sql,java;
    String draft;
    final ObjectMapper json=new ObjectMapper();
    @BeforeEach void setup()throws Exception {
        Path seed=root.resolve("src/main/resources/seed");Files.createDirectories(seed);
        for(String file:List.of("product-templates.json","cooking-processes.json","recipe-templates.json"))Files.copy(Path.of("src/main/resources/seed",file),seed.resolve(file));
        canonical=seed.resolve("recipe-templates.json");sql=root.resolve("src/main/resources/db/migration");java=root.resolve("src/main/java/db/migration");Files.createDirectories(sql);Files.createDirectories(java);
        Files.writeString(sql.resolve("V7__old.sql"),"original");Files.writeString(java.resolve("V41__old.java"),"original java");
        Files.writeString(java.resolve("V14_1__subversion.java"),"old subversion");
        draft=Files.readString(Path.of("docs/examples/recipe-template-draft.json"));
    }
    @Test void validationPreparesAddWithoutAnyWritesAndImportOnlyChangesTarget()throws Exception {
        var before=snapshot();var tool=new RecipeTemplateLocalImporter(root);var result=tool.validate(draft);
        assertThat(result.validation().update()).isFalse();assertThat(result.textSteps()).isPositive();assertThat(snapshot()).isEqualTo(before);
        var imported=tool.importDraft(draft);assertThat(imported.migration()).isEqualTo("V42__add_dill_potatoes_example.sql");
        assertThat(Files.readString(sql.resolve(imported.migration()))).doesNotContain("V_NEXT");
        assertThat(Files.readString(sql.resolve("V7__old.sql"))).isEqualTo("original");
        var old=json.readTree(before.get("src/main/resources/seed/recipe-templates.json")).get("recipes");
        var added=json.readTree(Files.readString(canonical)).get("recipes");
        for(int i=0;i<old.size();i++)assertThat(added.get(i)).isEqualTo(old.get(i));
        assertThat(added.size()).isEqualTo(old.size()+1);
    }
    @Test void updatePreservesParentIdentityAndHistoricalMigration()throws Exception {
        var tool=new RecipeTemplateLocalImporter(root);var first=tool.importDraft(draft);String original=Files.readString(sql.resolve(first.migration()));
        var before=json.readTree(Files.readString(canonical)).get("recipes");String id=before.get(before.size()-1).get("id").asText();
        var changed=(ObjectNode)json.readTree(draft);changed.put("name","Nyt navn");
        assertThat(tool.validate(changed.toString()).validation().update()).isTrue();
        var second=tool.importDraft(changed.toString());assertThat(second.migration()).startsWith("V43__update_");
        assertThat(Files.readString(sql.resolve(second.migration()))).contains(id,"DELETE FROM recipe_template_steps").doesNotContain("DELETE FROM recipes");
        assertThat(Files.readString(sql.resolve(first.migration()))).isEqualTo(original);
        assertThat(json.readTree(Files.readString(canonical)).get("recipes").get(before.size()-1).get("id").asText()).isEqualTo(id);
    }
    @Test void failedMigrationPublicationRollsBackCanonicalAndRemovesStagingFiles()throws Exception {
        var before=snapshot();var failing=new RecipeTemplateLocalImporter(root){@Override protected void publishMigration(Path from,Path to)throws IOException {throw new IOException("disk failure");}};
        assertThatThrownBy(()->failing.importDraft(draft)).isInstanceOf(IOException.class);
        var after=snapshot();after.remove("build/recipe-template-import.lock");assertThat(after).isEqualTo(before);
    }
    @Test void concurrentMigrationIsNeverOverwrittenAndCanonicalRollsBack()throws Exception {
        String before=Files.readString(canonical);
        var racing=new RecipeTemplateLocalImporter(root){@Override protected void publishMigration(Path from,Path to)throws IOException {Files.writeString(to,"another writer",StandardOpenOption.CREATE_NEW);super.publishMigration(from,to);}};
        assertThatThrownBy(()->racing.importDraft(draft)).isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readString(canonical)).isEqualTo(before);assertThat(Files.readString(sql.resolve("V42__add_dill_potatoes_example.sql"))).isEqualTo("another writer");
    }
    @Test void unsafeOrDuplicateVersionsFailBeforeChangingCanonical()throws Exception {
        String before=Files.readString(canonical);Files.writeString(sql.resolve("V_NEXT__bad.sql"),"bad");
        assertThatThrownBy(()->new RecipeTemplateLocalImporter(root).importDraft(draft)).isInstanceOf(IOException.class).hasMessageContaining("Cannot safely determine");
        assertThat(Files.readString(canonical)).isEqualTo(before);
        Files.delete(sql.resolve("V_NEXT__bad.sql"));Files.writeString(sql.resolve("V41__duplicate.sql"),"duplicate");
        assertThatThrownBy(()->new RecipeTemplateLocalImporter(root).nextVersion()).isInstanceOf(IOException.class).hasMessageContaining("Duplicate");
    }
    @Test void malformedAndDomainFailuresWriteNothing()throws Exception {
        var before=snapshot();var tool=new RecipeTemplateLocalImporter(root);
        for(String input:List.of("{", "null", draft+" {}",draft.replace("KARTOFFEL","NO_PRODUCT"),draft.replace("BOIL_POTATOES","NO_PROCESS"),draft.replace("\"quantity\": 250, \"unit\": \"GRAM\"}","\"quantity\": 251, \"unit\": \"GRAM\"}"))) {
            assertThatThrownBy(()->tool.validate(input)).isInstanceOfAny(IOException.class,IllegalArgumentException.class);
            assertThat(snapshot()).isEqualTo(before);
        }
    }
    private Map<String,String> snapshot()throws IOException {
        Map<String,String> result=new TreeMap<>();try(var files=Files.walk(root)){for(var file:files.filter(Files::isRegularFile).toList())result.put(root.relativize(file).toString().replace('\\','/'),Files.readString(file));}return result;
    }
}
