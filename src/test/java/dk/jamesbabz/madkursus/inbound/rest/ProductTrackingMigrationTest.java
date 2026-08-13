package dk.jamesbabz.madkursus.inbound.rest;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProductTrackingMigrationTest {
    @Test
    void migrationDefaultsExistingProductsAndPreservesRelationships() throws Exception {
        try (var stream = getClass().getResourceAsStream("/db/migration/V9__product_tracking_and_template_origin.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains("inventory_tracking_mode VARCHAR(16) NOT NULL DEFAULT 'QUANTITY'",
                    "source_template_id UUID NULL REFERENCES product_templates(id)",
                    "products_user_source_template_unique",
                    "inventory_items ALTER COLUMN quantity DROP NOT NULL",
                    "shopping_list_items ALTER COLUMN quantity DROP NOT NULL",
                    "inventory_was_present BOOLEAN NULL");
            assertThat(sql).doesNotContain("ON DELETE CASCADE");
        }
    }

    @Test
    void templateDefaultsMigrationKeepsRecipeUnitsAndSelectsPracticalPresenceItems() throws Exception {
        try (var stream = getClass().getResourceAsStream("/db/migration/V10__product_template_default_tracking_mode.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains("default_tracking_mode VARCHAR(16) NOT NULL DEFAULT 'QUANTITY'",
                    "category = 'SPICE'", "'Tørret oregano'", "'Ketchup'",
                    "'Sojasauce'", "'Olivenolie'", "'Lagereddike'", "'Tomatpuré'", "'Vaniljeekstrakt'");
            assertThat(sql).doesNotContain("default_unit =", "'Hakket oksekød'", "'Æg'");
        }
    }
}
