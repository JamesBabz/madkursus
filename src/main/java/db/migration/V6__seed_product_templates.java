package db.migration;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V6__seed_product_templates extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (var stream = getClass().getResourceAsStream("/db/seed/madkursus-product-templates-seed.json")) {
            if (stream == null) throw new IllegalStateException("Product template seed is missing");
            JsonNode products = new ObjectMapper().readTree(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .get("products");
            try (PreparedStatement template = context.getConnection().prepareStatement(
                    "INSERT INTO product_templates(id,name,normalized_name,category,default_unit,common) VALUES (?,?,?,?,?,?)");
                 PreparedStatement alias = context.getConnection().prepareStatement(
                    "INSERT INTO product_template_aliases(template_id,alias,normalized_alias) VALUES (?,?,?)")) {
                for (JsonNode product : products) {
                    String name = product.get("name").asText();
                    String normalized = normalize(name);
                    UUID id = UUID.nameUUIDFromBytes(("madkursus-template:" + normalized).getBytes(StandardCharsets.UTF_8));
                    template.setObject(1, id); template.setString(2, name); template.setString(3, normalized);
                    template.setString(4, product.get("category").asText());
                    template.setString(5, product.get("defaultUnit").asText());
                    template.setBoolean(6, product.get("common").asBoolean()); template.addBatch();
                    for (JsonNode value : product.get("aliases")) {
                        alias.setObject(1, id); alias.setString(2, value.asText());
                        alias.setString(3, normalize(value.asText())); alias.addBatch();
                    }
                }
                template.executeBatch(); alias.executeBatch();
            }
        }
    }

    private String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT); }
}
