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
    @Test void processKnowledgeMigrationIsAdditiveAndPreservesRecipeIngredients() throws Exception {
        try(var stream=getClass().getClassLoader().getResourceAsStream("db/migration/V27__process_owned_values.sql")) {
            assertThat(stream).isNotNull();String sql=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            assertThat(sql).contains("value_source","derived_rule","POTATO_WATER_PER_GRAM","PASTA_SALT_PER_GRAM","ADDITIONS:");
            assertThat(sql).doesNotContain("DELETE FROM recipe_ingredients","DELETE FROM recipes","DROP TABLE");
        }
    }
    @Test void timingAndPreparationMigrationIsAdditive() throws Exception {
        try(var stream=getClass().getClassLoader().getResourceAsStream("db/migration/V28__process_timing_and_preparation.sql")) {
            assertThat(stream).isNotNull();String sql=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            assertThat(sql).contains("active_duration_seconds","passive_duration_seconds","cooking_process_preparation_requirements","passive_duration_parameter_key","Tilsæt {SALT} salt.");
            assertThat(sql).doesNotContain("DELETE FROM recipes","DELETE FROM recipe_ingredients","DROP TABLE");
        }
    }
    @Test void preparedComponentsMigrationOwnsAllocationsWithoutChangingRequirements() throws Exception {try(var stream=getClass().getClassLoader().getResourceAsStream("db/migration/V29__prepared_components.sql")){assertThat(stream).isNotNull();String sql=new String(stream.readAllBytes(),StandardCharsets.UTF_8);assertThat(sql).contains("recipe_prepared_components","recipe_prepared_component_ingredients","recipe_template_prepared_components","prepared_component_id");assertThat(sql).doesNotContain("DELETE FROM recipe_ingredients","CREATE TABLE products","CREATE TABLE inventory");}}
    @Test void structuredInstructionMigrationIsNullableAndAdditive() throws Exception {try(var stream=getClass().getClassLoader().getResourceAsStream("db/migration/V30__structured_recipe_instructions.sql")){assertThat(stream).isNotNull();String sql=new String(stream.readAllBytes(),StandardCharsets.UTF_8);assertThat(sql).contains("recipe_preparation_steps ADD COLUMN structured_instruction JSONB","recipe_template_preparation_steps ADD COLUMN structured_instruction JSONB","recipe_steps ADD COLUMN structured_instruction JSONB","recipe_template_steps ADD COLUMN structured_instruction JSONB");assertThat(sql).doesNotContain("DROP","DELETE","UPDATE recipes");}}
    @Test void untrackedWaterMigrationOnlyWidensTrackingConstraintsAndAddsCanonicalTemplate() throws Exception {try(var stream=getClass().getClassLoader().getResourceAsStream("db/migration/V31__add_untracked_water_product_template.sql")){assertThat(stream).isNotNull();String sql=new String(stream.readAllBytes(),StandardCharsets.UTF_8);assertThat(sql).contains("'QUANTITY', 'PRESENCE', 'UNTRACKED'","04a53a53-364c-373c-8fe9-68e4146652d4","'Vand'","'UNTRACKED'");assertThat(sql).doesNotContain("UPDATE products","DELETE FROM","DROP TABLE");}}
    @Test void allocationMigrationAddsConcreteIngredientReferencesWithoutDestructiveChanges() throws Exception {
        try(var stream=getClass().getClassLoader().getResourceAsStream("db/migration/V20__bind_processes_to_recipe_ingredients.sql")) {
            assertThat(stream).isNotNull();String sql=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            assertThat(sql).contains("recipe_ingredient_id UUID REFERENCES recipe_ingredients(id)","recipe_ingredient_id UUID REFERENCES recipe_template_ingredients(id)","HAVING COUNT(*) = 1");
            assertThat(sql).doesNotContain("DROP TABLE","DELETE FROM recipe_ingredients");
        }
    }
}
