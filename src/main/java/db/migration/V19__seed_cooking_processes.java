package db.migration;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V19__seed_cooking_processes extends BaseJavaMigration {
    private static final Instant SEEDED_AT=Instant.parse("2026-08-14T00:00:00Z");
    private static final Pattern PLACEHOLDER=Pattern.compile("\\{([A-Z][A-Z0-9_]*)}");

    @Override public void migrate(Context context)throws Exception {
        try(var stream=getClass().getResourceAsStream("/db/seed/madkursus-cooking-processes-seed.json")) {
            if(stream==null)throw new IllegalStateException("Cooking process seed is missing");
            JsonNode processes=new ObjectMapper().readTree(stream).get("processes");
            try(PreparedStatement process=context.getConnection().prepareStatement("INSERT INTO cooking_processes(id,process_key,name,description,completion_criteria_template,active,created_at,updated_at) VALUES (?,?,?,?,?,true,?,?)");
                PreparedStatement parameter=context.getConnection().prepareStatement("INSERT INTO cooking_process_parameters(id,cooking_process_id,parameter_key,label,parameter_type,required,unit,default_quantity,default_unit,default_duration_seconds,default_temperature_celsius,default_heat_level,default_number,default_text,sort_order) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                PreparedStatement step=context.getConnection().prepareStatement("INSERT INTO cooking_process_steps(id,cooking_process_id,instruction_template,sort_order) VALUES (?,?,?,?)");
                PreparedStatement equipment=context.getConnection().prepareStatement("INSERT INTO cooking_process_equipment_requirements(id,cooking_process_id,equipment_type,requirement_level) VALUES (?,?,?,?)")) {
                for(JsonNode value:processes) {
                    String key=value.get("key").asText(); UUID processId=id("cooking-process:"+key);
                    Set<String> keys=new HashSet<>(); value.get("parameters").forEach(p->{if(!keys.add(p.get("key").asText()))throw new IllegalStateException("Duplicate parameter key in "+key);});
                    value.get("steps").forEach(s->validateTemplate(key,s.asText(),keys));
                    validateTemplate(key,value.get("completionCriteria").asText(),keys);
                    process.setObject(1,processId);process.setString(2,key);process.setString(3,value.get("name").asText());process.setString(4,text(value,"description"));process.setString(5,value.get("completionCriteria").asText());process.setTimestamp(6,Timestamp.from(SEEDED_AT));process.setTimestamp(7,Timestamp.from(SEEDED_AT));process.executeUpdate();
                    int order=1;
                    for(JsonNode p:value.get("parameters")) {
                        JsonNode d=p.path("default");
                        parameter.setObject(1,id("cooking-process-parameter:"+key+":"+p.get("key").asText()));parameter.setObject(2,processId);parameter.setString(3,p.get("key").asText());parameter.setString(4,p.get("label").asText());parameter.setString(5,p.get("type").asText());parameter.setBoolean(6,p.get("required").asBoolean());parameter.setString(7,text(p,"unit"));
                        decimal(parameter,8,d,"quantity");parameter.setString(9,text(d,"unit"));integer(parameter,10,d,"durationSeconds");integer(parameter,11,d,"temperatureCelsius");parameter.setString(12,text(d,"heatLevel"));decimal(parameter,13,d,"number");parameter.setString(14,text(d,"text"));parameter.setInt(15,order++);parameter.addBatch();
                    }
                    order=1;for(JsonNode s:value.get("steps")){step.setObject(1,id("cooking-process-step:"+key+":"+order));step.setObject(2,processId);step.setString(3,s.asText());step.setInt(4,order++);step.addBatch();}
                    for(JsonNode e:value.get("equipment")){String type=e.get(0).asText();equipment.setObject(1,id("cooking-process-equipment:"+key+":"+type));equipment.setObject(2,processId);equipment.setString(3,type);equipment.setString(4,e.get(1).asText());equipment.addBatch();}
                }
                parameter.executeBatch();step.executeBatch();equipment.executeBatch();
            }
        }
    }
    private void validateTemplate(String process,String template,Set<String> keys){Matcher m=PLACEHOLDER.matcher(template);while(m.find())if(!keys.contains(m.group(1)))throw new IllegalStateException("Unknown placeholder "+m.group(1)+" in "+process);String rest=m.replaceAll("");if(rest.contains("{")||rest.contains("}"))throw new IllegalStateException("Invalid placeholder syntax in "+process);}
    private UUID id(String value){return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));}
    private String text(JsonNode node,String field){JsonNode value=node.get(field);return value==null||value.isNull()?null:value.asText();}
    private void integer(PreparedStatement statement,int index,JsonNode node,String field)throws Exception{JsonNode value=node.get(field);if(value==null||value.isNull())statement.setObject(index,null);else statement.setInt(index,value.asInt());}
    private void decimal(PreparedStatement statement,int index,JsonNode node,String field)throws Exception{JsonNode value=node.get(field);if(value==null||value.isNull())statement.setObject(index,null);else statement.setBigDecimal(index,value.decimalValue());}
}
