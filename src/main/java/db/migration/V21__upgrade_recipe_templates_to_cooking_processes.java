package db.migration;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Explicit, versioned import of V21's immutable RecipeTemplate snapshot. It only
 * replaces children owned by global templates; copied user Recipes are never touched.
 */
public class V21__upgrade_recipe_templates_to_cooking_processes extends BaseJavaMigration {
    private static final Instant UPDATED_AT = Instant.parse("2026-08-14T00:00:00Z");

    @Override
    public void migrate(Context context) throws Exception {
        JsonNode recipes;
        try (var stream = getClass().getResourceAsStream("/db/migration/data/V21__recipe_templates.json")) {
            if (stream == null) throw new IllegalStateException("V21 RecipeTemplate snapshot is missing");
            recipes = new ObjectMapper().readTree(stream).path("recipes");
        }
        if (!recipes.isArray() || recipes.size() != 15)
            throw new IllegalStateException("V21 RecipeTemplate snapshot must contain the 15 curated templates");

        Set<String> names = new HashSet<>();
        Set<String> recipeKeys = new HashSet<>();
        for (JsonNode recipe : recipes) {
            String name = requiredText(recipe, "name");
            String recipeKey = requiredText(recipe, "key");
            if (!names.add(normalize(name)) || !recipeKeys.add(recipeKey))
                throw new IllegalStateException("RecipeTemplate names and keys must be unique: " + name);
            importRecipe(context, recipe);
        }
    }

    private void importRecipe(Context context, JsonNode recipe) throws Exception {
        String name = requiredText(recipe, "name");
        UUID recipeId = stableId("recipe-template:" + normalize(name));
        ensureTemplateExists(context, recipeId, name);

        try (PreparedStatement update = context.getConnection().prepareStatement(
                "UPDATE recipe_templates SET name=?, normalized_name=?, description=?, active=true, updated_at=? WHERE id=?")) {
            update.setString(1, name);
            update.setString(2, normalize(name));
            update.setString(3, recipe.path("description").asText(null));
            update.setTimestamp(4, Timestamp.from(UPDATED_AT));
            update.setObject(5, recipeId);
            update.executeUpdate();
        }
        try (PreparedStatement deleteBindings = context.getConnection().prepareStatement(
                "DELETE FROM recipe_template_process_bindings WHERE recipe_template_step_id IN (SELECT id FROM recipe_template_steps WHERE recipe_template_id=?)");
             PreparedStatement deleteSteps = context.getConnection().prepareStatement(
                "DELETE FROM recipe_template_steps WHERE recipe_template_id=?");
             PreparedStatement deleteIngredients = context.getConnection().prepareStatement(
                "DELETE FROM recipe_template_ingredients WHERE recipe_template_id=?")) {
            deleteBindings.setObject(1, recipeId); deleteBindings.executeUpdate();
            deleteSteps.setObject(1, recipeId); deleteSteps.executeUpdate();
            deleteIngredients.setObject(1, recipeId); deleteIngredients.executeUpdate();
        }

        Map<String, Ingredient> ingredients = insertIngredients(context, recipeId, recipe.path("ingredients"));
        insertSteps(context, recipeId, recipe.path("steps"), ingredients);
    }

    private Map<String, Ingredient> insertIngredients(Context context, UUID recipeId, JsonNode values) throws Exception {
        if (!values.isArray() || values.isEmpty()) throw new IllegalStateException("RecipeTemplate requires ingredients");
        Map<String, Ingredient> result = new HashMap<>();
        try (PreparedStatement insert = context.getConnection().prepareStatement(
                "INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) VALUES (?,?,?,?,?,?,?)")) {
            int order = 1;
            for (JsonNode value : values) {
                String key = requiredText(value, "key");
                if (result.containsKey(key)) throw new IllegalStateException("Duplicate RecipeTemplate ingredient key: " + key);
                UUID productId = findProductTemplate(context, requiredText(value, "productTemplate"));
                BigDecimal quantity = positiveDecimal(value, "quantity");
                String unit = requiredText(value, "unit");
                UUID ingredientId = stableId("recipe-template-ingredient:" + recipeId + ":" + key);
                Ingredient ingredient = new Ingredient(ingredientId, productId, quantity, unit);
                result.put(key, ingredient);
                insert.setObject(1, ingredientId); insert.setObject(2, recipeId); insert.setObject(3, productId);
                insert.setBigDecimal(4, quantity); insert.setString(5, unit);
                insert.setString(6, value.path("preparation").asText(null)); insert.setInt(7, order++); insert.addBatch();
            }
            insert.executeBatch();
        }
        return result;
    }

    private void insertSteps(Context context, UUID recipeId, JsonNode steps, Map<String, Ingredient> ingredients) throws Exception {
        if (!steps.isArray() || steps.isEmpty()) throw new IllegalStateException("RecipeTemplate requires steps");
        Map<UUID, BigDecimal> allocatedBase = new HashMap<>();
        try (PreparedStatement insertStep = context.getConnection().prepareStatement(
                "INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,sort_order,step_type,cooking_process_id) VALUES (?,?,?,?,?,?)");
             PreparedStatement insertBinding = context.getConnection().prepareStatement(
                "INSERT INTO recipe_template_process_bindings(id,recipe_template_step_id,parameter_key,recipe_ingredient_id,product_template_id,quantity,unit,duration_seconds,temperature_celsius,heat_level,number_value,text_value) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
            int order = 1;
            for (JsonNode step : steps) {
                String type = requiredText(step, "type");
                UUID stepId = stableId("recipe-template-step:" + recipeId + ":" + order);
                UUID processId = null;
                String instruction = null;
                if ("TEXT".equals(type)) instruction = requiredText(step, "instruction");
                else if ("PROCESS".equals(type)) processId = findCookingProcess(context, requiredText(step, "process"));
                else throw new IllegalStateException("Unknown RecipeTemplate step type: " + type);
                insertStep.setObject(1, stepId); insertStep.setObject(2, recipeId); insertStep.setString(3, instruction);
                insertStep.setInt(4, order); insertStep.setString(5, type); insertStep.setObject(6, processId); insertStep.addBatch();
                if (processId != null) insertBindings(context, processId, stepId, step.path("bindings"), ingredients, allocatedBase, insertBinding);
                order++;
            }
            insertStep.executeBatch();
            insertBinding.executeBatch();
        }
    }

    private void insertBindings(Context context, UUID processId, UUID stepId, JsonNode bindings,
            Map<String, Ingredient> ingredients, Map<UUID, BigDecimal> allocatedBase, PreparedStatement insert) throws Exception {
        Map<String, Parameter> parameters = loadParameters(context, processId);
        if (!bindings.isObject()) throw new IllegalStateException("PROCESS step bindings must be an object");
        Set<String> supplied = new HashSet<>();
        var fields = bindings.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            String parameterKey = field.getKey();
            JsonNode value = field.getValue();
            Parameter parameter = parameters.get(parameterKey);
            if (parameter == null) throw new IllegalStateException("Unknown CookingProcess parameter: " + parameterKey);
            if (!supplied.add(parameterKey)) throw new IllegalStateException("Duplicate CookingProcess binding: " + parameterKey);

            Ingredient ingredient = null;
            BigDecimal quantity = value.hasNonNull("quantity") ? positiveDecimal(value, "quantity") : null;
            String unit = value.path("unit").asText(null);
            if ("INGREDIENT_QUANTITY".equals(parameter.type())) {
                String ingredientKey = requiredText(value, "ingredient");
                ingredient = ingredients.get(ingredientKey);
                if (ingredient == null) throw new IllegalStateException("Unknown RecipeTemplate ingredient: " + ingredientKey);
                if (quantity == null || unit == null) throw new IllegalStateException("Ingredient binding requires quantity and unit");
                BigDecimal total = baseQuantity(ingredient.quantity(), ingredient.unit());
                BigDecimal used = baseQuantity(quantity, unit);
                if (!dimension(ingredient.unit()).equals(dimension(unit)))
                    throw new IllegalStateException("Incompatible RecipeTemplate allocation unit for " + ingredientKey);
                BigDecimal allocated = allocatedBase.merge(ingredient.id(), used, BigDecimal::add);
                if (allocated.compareTo(total) > 0)
                    throw new IllegalStateException("RecipeTemplate process allocation exceeds ingredient total: " + ingredientKey);
            }
            insert.setObject(1, stableId("recipe-template-binding:" + stepId + ":" + parameterKey));
            insert.setObject(2, stepId); insert.setString(3, parameterKey);
            insert.setObject(4, ingredient == null ? null : ingredient.id());
            insert.setObject(5, ingredient == null ? null : ingredient.productId());
            setDecimal(insert, 6, quantity); insert.setString(7, unit);
            setInteger(insert, 8, value, "durationSeconds"); setInteger(insert, 9, value, "temperatureCelsius");
            insert.setString(10, value.path("heatLevel").asText(null));
            setDecimal(insert, 11, value.hasNonNull("number") ? value.get("number").decimalValue() : null);
            insert.setString(12, value.path("text").asText(null)); insert.addBatch();
        }
        for (Parameter parameter : parameters.values()) {
            if (parameter.required() && !parameter.hasDefault() && !supplied.contains(parameter.key()))
                throw new IllegalStateException("Missing required CookingProcess binding: " + parameter.key());
        }
    }

    private Map<String, Parameter> loadParameters(Context context, UUID processId) throws Exception {
        Map<String, Parameter> result = new HashMap<>();
        try (PreparedStatement query = context.getConnection().prepareStatement(
                "SELECT parameter_key,parameter_type,required,(default_quantity IS NOT NULL OR default_duration_seconds IS NOT NULL OR default_temperature_celsius IS NOT NULL OR default_heat_level IS NOT NULL OR default_number IS NOT NULL OR default_text IS NOT NULL) FROM cooking_process_parameters WHERE cooking_process_id=?")) {
            query.setObject(1, processId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) result.put(rows.getString(1), new Parameter(rows.getString(1), rows.getString(2), rows.getBoolean(3), rows.getBoolean(4)));
            }
        }
        return result;
    }

    private void ensureTemplateExists(Context context, UUID id, String name) throws Exception {
        try (PreparedStatement query = context.getConnection().prepareStatement("SELECT 1 FROM recipe_templates WHERE id=?")) {
            query.setObject(1, id);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) throw new IllegalStateException("Expected existing RecipeTemplate from V15: " + name);
            }
        }
    }

    private UUID findProductTemplate(Context context, String name) throws Exception {
        return findId(context, "SELECT id FROM product_templates WHERE normalized_name=?", normalize(name), "ProductTemplate", name);
    }

    private UUID findCookingProcess(Context context, String key) throws Exception {
        return findId(context, "SELECT id FROM cooking_processes WHERE process_key=? AND active=true", key, "CookingProcess", key);
    }

    private UUID findId(Context context, String sql, String value, String type, String display) throws Exception {
        try (PreparedStatement query = context.getConnection().prepareStatement(sql)) {
            query.setString(1, value);
            try (ResultSet result = query.executeQuery()) {
                if (result.next()) return result.getObject(1, UUID.class);
            }
        }
        throw new IllegalStateException("RecipeTemplate seed references unknown " + type + ": " + display);
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) throw new IllegalStateException("RecipeTemplate seed field is required: " + field);
        return value;
    }

    private BigDecimal positiveDecimal(JsonNode node, String field) {
        if (!node.hasNonNull(field) || !node.get(field).isNumber() || node.get(field).decimalValue().signum() <= 0)
            throw new IllegalStateException("RecipeTemplate seed requires a positive number: " + field);
        return node.get(field).decimalValue();
    }

    private BigDecimal baseQuantity(BigDecimal quantity, String unit) {
        return quantity.multiply(switch (unit) {
            case "MILLILITER", "GRAM", "PIECE" -> BigDecimal.ONE;
            case "TEASPOON" -> BigDecimal.valueOf(5);
            case "TABLESPOON" -> BigDecimal.valueOf(15);
            case "DECILITER" -> BigDecimal.valueOf(100);
            default -> throw new IllegalStateException("Unknown RecipeUnit: " + unit);
        });
    }

    private String dimension(String unit) {
        return switch (unit) {
            case "MILLILITER", "TEASPOON", "TABLESPOON", "DECILITER" -> "VOLUME";
            case "GRAM" -> "MASS";
            case "PIECE" -> "COUNT";
            default -> throw new IllegalStateException("Unknown RecipeUnit: " + unit);
        };
    }

    private void setDecimal(PreparedStatement statement, int index, BigDecimal value) throws Exception {
        if (value == null) statement.setNull(index, java.sql.Types.NUMERIC); else statement.setBigDecimal(index, value);
    }

    private void setInteger(PreparedStatement statement, int index, JsonNode value, String field) throws Exception {
        if (value.hasNonNull(field)) statement.setInt(index, value.get(field).intValue()); else statement.setNull(index, java.sql.Types.INTEGER);
    }

    private UUID stableId(String value) { return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)); }
    private String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT); }

    private record Ingredient(UUID id, UUID productId, BigDecimal quantity, String unit) {}
    private record Parameter(String key, String type, boolean required, boolean hasDefault) {}
}
