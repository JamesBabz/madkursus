package db.migration;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V15__seed_recipe_templates extends BaseJavaMigration {
    private static final Instant SEEDED_AT = Instant.parse("2026-08-13T00:00:00Z");

    @Override
    public void migrate(Context context) throws Exception {
        try (var stream = getClass().getResourceAsStream("/db/seed/madkursus-recipe-templates-seed.json")) {
            if (stream == null) throw new IllegalStateException("Recipe template seed is missing");
            JsonNode recipes = new ObjectMapper().readTree(stream).get("recipes");
            try (PreparedStatement template = context.getConnection().prepareStatement(
                    "INSERT INTO recipe_templates(id,name,normalized_name,description,active,created_at,updated_at) VALUES (?,?,?,?,true,?,?)");
                 PreparedStatement ingredient = context.getConnection().prepareStatement(
                    "INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) VALUES (?,?,?,?,?,?,?)");
                 PreparedStatement step = context.getConnection().prepareStatement(
                    "INSERT INTO recipe_template_steps(id,recipe_template_id,sort_order,instruction) VALUES (?,?,?,?)")) {
                for (JsonNode recipe : recipes) {
                    String name = recipe.get("name").asText();
                    UUID recipeId = stableId("recipe-template:" + normalize(name));
                    template.setObject(1, recipeId);
                    template.setString(2, name);
                    template.setString(3, normalize(name));
                    template.setString(4, recipe.path("description").asText(null));
                    template.setTimestamp(5, Timestamp.from(SEEDED_AT));
                    template.setTimestamp(6, Timestamp.from(SEEDED_AT));
                    template.addBatch();

                    int order = 1;
                    for (JsonNode value : recipe.get("ingredients")) {
                        UUID productTemplateId = findProductTemplate(context, value.get(0).asText());
                        ingredient.setObject(1, stableId("recipe-template-ingredient:" + recipeId + ":" + order));
                        ingredient.setObject(2, recipeId);
                        ingredient.setObject(3, productTemplateId);
                        ingredient.setBigDecimal(4, value.get(1).decimalValue());
                        ingredient.setString(5, value.get(2).asText());
                        ingredient.setString(6, value.size() > 3 ? value.get(3).asText() : null);
                        ingredient.setInt(7, order++);
                        ingredient.addBatch();
                    }

                    order = 1;
                    for (JsonNode instruction : recipe.get("steps")) {
                        step.setObject(1, stableId("recipe-template-step:" + recipeId + ":" + order));
                        step.setObject(2, recipeId);
                        step.setInt(3, order++);
                        step.setString(4, instruction.asText());
                        step.addBatch();
                    }
                }
                template.executeBatch();
                ingredient.executeBatch();
                step.executeBatch();
            }
        }
    }

    private UUID findProductTemplate(Context context, String name) throws Exception {
        try (PreparedStatement query = context.getConnection().prepareStatement(
                "SELECT id FROM product_templates WHERE normalized_name = ?")) {
            query.setString(1, normalize(name));
            try (ResultSet result = query.executeQuery()) {
                if (result.next()) return result.getObject(1, UUID.class);
            }
        }
        throw new IllegalStateException("Recipe template seed references unknown ProductTemplate: " + name);
    }

    private UUID stableId(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
