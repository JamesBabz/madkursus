package dk.jamesbabz.madkursus.inbound.rest;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CookingProcessMigrationTest {
    @Test void migrationIsAdditiveNormalizedAndPreservesTextSteps() throws Exception {
        try(var stream=getClass().getClassLoader().getResourceAsStream("db/migration/V18__add_cooking_processes.sql")) {
            assertThat(stream).isNotNull(); String sql=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            assertThat(sql).contains("CREATE TABLE cooking_processes", "CREATE TABLE cooking_process_parameters",
                    "CREATE TABLE cooking_process_steps", "CREATE TABLE cooking_process_equipment_requirements",
                    "CREATE TABLE recipe_process_bindings", "CREATE TABLE recipe_template_process_bindings",
                    "step_type VARCHAR(16) NOT NULL DEFAULT 'TEXT'", "cooking_process_id UUID REFERENCES cooking_processes(id)");
            assertThat(sql).doesNotContain("DROP TABLE", "DELETE FROM recipe_steps", "DELETE FROM recipe_template_steps");
        }
    }
    @Test void allocationMigrationAddsConcreteIngredientReferencesWithoutDestructiveChanges() throws Exception {
        try(var stream=getClass().getClassLoader().getResourceAsStream("db/migration/V20__bind_processes_to_recipe_ingredients.sql")) {
            assertThat(stream).isNotNull();String sql=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            assertThat(sql).contains("recipe_ingredient_id UUID REFERENCES recipe_ingredients(id)","recipe_ingredient_id UUID REFERENCES recipe_template_ingredients(id)","HAVING COUNT(*) = 1");
            assertThat(sql).doesNotContain("DROP TABLE","DELETE FROM recipe_ingredients");
        }
    }
}
