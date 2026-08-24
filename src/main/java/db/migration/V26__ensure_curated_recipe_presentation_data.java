package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Idempotent follow-up for databases where V25 ran before the presentation source was packaged. */
public class V26__ensure_curated_recipe_presentation_data extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        UUID recipe=find(context,"kødboller i tomatsovs med pasta");
        JsonNode source=read().path("recipe");
        try(PreparedStatement insert=context.getConnection().prepareStatement(
                "INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,instruction,sort_order) VALUES (?,?,?,?) ON CONFLICT (recipe_template_id,sort_order) DO NOTHING")) {
            int order=1;
            for(JsonNode text:source.path("preparation")) {
                insert.setObject(1,id("recipe-template-preparation:"+recipe+":"+order));
                insert.setObject(2,recipe);insert.setString(3,text.asText());insert.setInt(4,order++);insert.addBatch();
            }
            insert.executeBatch();
        }
        try(PreparedStatement insert=context.getConnection().prepareStatement(
                "INSERT INTO recipe_template_equipment_requirements(id,recipe_template_id,equipment_type,label,sort_order) VALUES (?,?,?,?,?) ON CONFLICT (recipe_template_id,sort_order) DO NOTHING")) {
            int order=1;
            for(JsonNode value:source.path("equipment")) {
                insert.setObject(1,id("recipe-template-equipment:"+recipe+":"+order));insert.setObject(2,recipe);
                insert.setString(3,value.path("equipmentType").asText(null));insert.setString(4,value.path("label").asText(null));
                insert.setInt(5,order++);insert.addBatch();
            }
            insert.executeBatch();
        }
        try(PreparedStatement update=context.getConnection().prepareStatement("""
                UPDATE recipe_template_process_bindings binding
                   SET product_template_id=(SELECT id FROM product_templates WHERE normalized_name='letmælk')
                  FROM recipe_template_steps step
                 WHERE step.id=binding.recipe_template_step_id AND step.recipe_template_id=?
                   AND binding.parameter_key='LIQUID'
                """)) { update.setObject(1,recipe);update.executeUpdate(); }
    }

    private UUID find(Context context,String name)throws Exception {
        try(PreparedStatement statement=context.getConnection().prepareStatement("SELECT id FROM recipe_templates WHERE normalized_name=?")) {
            statement.setString(1,name.toLowerCase(Locale.ROOT));
            try(ResultSet result=statement.executeQuery()){if(result.next())return result.getObject(1,UUID.class);}
        }
        throw new IllegalStateException("Missing curated RecipeTemplate: "+name);
    }
    private JsonNode read()throws Exception {try(var input=getClass().getResourceAsStream("/db/migration/data/V23__curated_meatballs.json")){return new ObjectMapper().readTree(input);}}
    private UUID id(String value){return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));}
}
