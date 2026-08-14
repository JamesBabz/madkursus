package dk.jamesbabz.madkursus.migration;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RecipeDeletionMigrationTest {
    @Test void historicalPlannedRecipesReceiveANameSnapshotAndNullableLiveReference() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V17__preserve_historical_planned_recipes.sql"));
        assertThat(sql).contains("ADD COLUMN recipe_name", "ALTER COLUMN recipe_name SET NOT NULL", "ALTER COLUMN recipe_id DROP NOT NULL")
                .doesNotContain("ON DELETE CASCADE", "DROP CONSTRAINT");
    }

    @Test void existingOwnershipAndHistoryForeignKeysHaveDeliberateDeleteRules() throws Exception {
        String recipes=Files.readString(Path.of("src/main/resources/db/migration/V11__add_recipes.sql"));
        String history=Files.readString(Path.of("src/main/resources/db/migration/V12__add_recipe_cook_history.sql"));
        String templates=Files.readString(Path.of("src/main/resources/db/migration/V14__add_recipe_templates.sql"));
        assertThat(recipes).contains("recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE");
        assertThat(history).contains("recipe_id UUID REFERENCES recipes(id) ON DELETE SET NULL", "recipe_name VARCHAR(255) NOT NULL");
        assertThat(templates).contains("source_template_id UUID REFERENCES recipe_templates(id) ON DELETE SET NULL");
    }
}
