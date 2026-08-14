package dk.jamesbabz.madkursus.inbound.rest;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class KitchenEquipmentMigrationTest {
 @Test void migrationIsAdditiveOwnedAndEnforcesPreferredUniqueness() throws Exception {try(var stream=getClass().getClassLoader().getResourceAsStream("db/migration/V16__add_kitchen_equipment.sql")){assertThat(stream).isNotNull();String sql=new String(stream.readAllBytes(),StandardCharsets.UTF_8);assertThat(sql).contains("CREATE TABLE kitchen_equipment","user_id UUID NOT NULL REFERENCES users(id)","configuration JSONB NOT NULL","WHERE preferred = TRUE","UNIQUE(user_id, equipment_type, normalized_name)");assertThat(sql).doesNotContain("DROP TABLE","DELETE FROM users");}}
}
