package dk.jamesbabz.madkursus.outbound.cookingprocess;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.ports.CookingProcessPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @RequiredArgsConstructor
public class CookingProcessAdapterImpl implements CookingProcessPort {
    private final JdbcTemplate jdbc;

    @Override @Transactional(readOnly=true)
    public List<CookingProcess> search(String query) {
        String q="%"+(query==null?"":query.toLowerCase(Locale.ROOT))+"%";
        return jdbc.query("SELECT * FROM cooking_processes WHERE active=true AND (LOWER(name) LIKE ? OR LOWER(process_key) LIKE ?) ORDER BY name",(rs,n)->map(rs),q,q);
    }
    @Override @Transactional(readOnly=true)
    public Optional<CookingProcess> findById(UUID id) {
        return jdbc.query("SELECT * FROM cooking_processes WHERE id=? AND active=true",(rs,n)->map(rs),id).stream().findFirst();
    }
    private CookingProcess map(ResultSet rs) throws SQLException {
        UUID id=rs.getObject("id",UUID.class);
        List<CookingProcessParameter> parameters=jdbc.query("SELECT * FROM cooking_process_parameters WHERE cooking_process_id=? ORDER BY sort_order",(p,n)->new CookingProcessParameter(
                p.getObject("id",UUID.class),p.getString("parameter_key"),p.getString("label"),CookingProcessParameterType.valueOf(p.getString("parameter_type")),p.getBoolean("required"),
                new CookingProcessValue(p.getBigDecimal("default_quantity"),enumValue(RecipeUnit.class,p.getString("default_unit")),integer(p,"default_duration_seconds"),integer(p,"default_temperature_celsius"),enumValue(HeatLevel.class,p.getString("default_heat_level")),p.getBigDecimal("default_number"),p.getString("default_text")),
                enumValue(RecipeUnit.class,p.getString("unit")),p.getInt("sort_order"),
                enumValue(CookingProcessParameterSource.class,p.getString("value_source")),
                enumValue(CookingProcessDerivedRule.class,p.getString("derived_rule")),p.getString("derived_from")),id);
        List<CookingProcessStep> steps=jdbc.query("SELECT * FROM cooking_process_steps WHERE cooking_process_id=? ORDER BY sort_order",(s,n)->new CookingProcessStep(s.getObject("id",UUID.class),s.getString("instruction_template"),s.getInt("sort_order")),id);
        List<CookingProcessEquipmentRequirement> requirements=jdbc.query("SELECT * FROM cooking_process_equipment_requirements WHERE cooking_process_id=? ORDER BY equipment_type",(e,n)->new CookingProcessEquipmentRequirement(e.getObject("id",UUID.class),EquipmentType.valueOf(e.getString("equipment_type")),EquipmentRequirementLevel.valueOf(e.getString("requirement_level"))),id);
        List<CookingProcessPreparationRequirement> preparation=jdbc.query("SELECT * FROM cooking_process_preparation_requirements WHERE cooking_process_id=? ORDER BY sort_order",(p,n)->new CookingProcessPreparationRequirement(p.getObject("id",UUID.class),p.getString("parameter_key"),p.getString("instruction_template"),p.getInt("sort_order")),id);
        return new CookingProcess(id,rs.getString("process_key"),rs.getString("name"),rs.getString("description"),rs.getBoolean("active"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant(),parameters,steps,requirements,rs.getString("completion_criteria_template"),integer(rs,"active_duration_seconds"),integer(rs,"passive_duration_seconds"),rs.getString("active_duration_parameter_key"),rs.getString("passive_duration_parameter_key"),preparation);
    }
    private Integer integer(ResultSet rs,String name)throws SQLException{int value=rs.getInt(name);return rs.wasNull()?null:value;}
    private <E extends Enum<E>> E enumValue(Class<E> type,String value){return value==null?null:Enum.valueOf(type,value);}
}
