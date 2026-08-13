package db.migration;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V14_1__add_sausage_product_template extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        String normalized="pølser";
        UUID id=UUID.nameUUIDFromBytes(("madkursus-template:"+normalized).getBytes(StandardCharsets.UTF_8));
        try(PreparedStatement statement=context.getConnection().prepareStatement(
                "INSERT INTO product_templates(id,name,normalized_name,category,default_unit,default_tracking_mode,common) VALUES (?,?,?,?,?,?,?)")){
            statement.setObject(1,id); statement.setString(2,"Pølser"); statement.setString(3,normalized);
            statement.setString(4,"MEAT"); statement.setString(5,"GRAM"); statement.setString(6,"QUANTITY"); statement.setBoolean(7,false);
            statement.executeUpdate();
        }
    }
}
