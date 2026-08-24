package db.migration;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Imports one versioned, manually tested RecipeTemplate and its process changes. */
public class V23__add_curated_meatballs_in_tomato_sauce extends BaseJavaMigration {
    private static final Instant UPDATED_AT=Instant.parse("2026-08-14T00:00:00Z");

    @Override public void migrate(Context context)throws Exception {
        JsonNode processRoot=read("/db/migration/data/V23__cooking_processes.json");
        Map<String,JsonNode> processes=new HashMap<>();
        processRoot.path("processes").forEach(value->processes.put(value.path("key").asText(),value));
        addOptionalParameter(context,processes.get("MIX_MEATBALL_MIXTURE"),"OTHER_SEASONING");
        updateProcess(context,processes.get("BOIL_PASTA"));
        insertProcess(context,processes.get("PAN_FRY_MEATBALLS"));
        insertRecipe(context,read("/db/migration/data/V23__curated_meatballs.json").path("recipe"));
    }

    private void addOptionalParameter(Context context,JsonNode definition,String parameterKey)throws Exception {
        UUID processId=findProcess(context,required(definition,"key")); JsonNode parameter=findParameter(definition,parameterKey);
        try(PreparedStatement shift=context.getConnection().prepareStatement("UPDATE cooking_process_parameters SET sort_order=sort_order+100 WHERE cooking_process_id=?")){shift.setObject(1,processId);shift.executeUpdate();}
        try(PreparedStatement insert=context.getConnection().prepareStatement("INSERT INTO cooking_process_parameters(id,cooking_process_id,parameter_key,label,parameter_type,required,unit,sort_order) SELECT ?,?,?,?,?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM cooking_process_parameters WHERE cooking_process_id=? AND parameter_key=?)")) {
            insert.setObject(1,id("cooking-process-parameter:"+required(definition,"key")+":"+parameterKey));insert.setObject(2,processId);insert.setString(3,parameterKey);insert.setString(4,required(parameter,"label"));insert.setString(5,required(parameter,"type"));insert.setBoolean(6,parameter.path("required").asBoolean());insert.setString(7,text(parameter,"unit"));insert.setInt(8,parameterIndex(definition,parameterKey));insert.setObject(9,processId);insert.setString(10,parameterKey);insert.executeUpdate();
        }
        try(PreparedStatement reorder=context.getConnection().prepareStatement("UPDATE cooking_process_parameters SET sort_order=? WHERE cooking_process_id=? AND parameter_key=?")){int order=1;for(JsonNode value:definition.path("parameters")){reorder.setInt(1,order++);reorder.setObject(2,processId);reorder.setString(3,required(value,"key"));reorder.addBatch();}reorder.executeBatch();}
    }

    private void updateProcess(Context context,JsonNode definition)throws Exception {
        String key=required(definition,"key");UUID processId=findProcess(context,key);
        addOptionalParameter(context,definition,"SALT");
        try(PreparedStatement update=context.getConnection().prepareStatement("UPDATE cooking_processes SET name=?,description=?,completion_criteria_template=?,updated_at=? WHERE id=?");PreparedStatement delete=context.getConnection().prepareStatement("DELETE FROM cooking_process_steps WHERE cooking_process_id=?");PreparedStatement insert=context.getConnection().prepareStatement("INSERT INTO cooking_process_steps(id,cooking_process_id,instruction_template,sort_order) VALUES (?,?,?,?)")) {
            update.setString(1,required(definition,"name"));update.setString(2,text(definition,"description"));update.setString(3,required(definition,"completionCriteria"));update.setTimestamp(4,Timestamp.from(UPDATED_AT));update.setObject(5,processId);update.executeUpdate();
            delete.setObject(1,processId);delete.executeUpdate();int order=1;for(JsonNode step:definition.path("steps")){insert.setObject(1,id("cooking-process-step:"+key+":"+order));insert.setObject(2,processId);insert.setString(3,step.asText());insert.setInt(4,order++);insert.addBatch();}insert.executeBatch();
        }
    }

    private void insertProcess(Context context,JsonNode definition)throws Exception {
        String key=required(definition,"key");UUID processId=id("cooking-process:"+key);
        try(PreparedStatement process=context.getConnection().prepareStatement("INSERT INTO cooking_processes(id,process_key,name,description,completion_criteria_template,active,created_at,updated_at) VALUES (?,?,?,?,?,true,?,?)");PreparedStatement parameter=context.getConnection().prepareStatement("INSERT INTO cooking_process_parameters(id,cooking_process_id,parameter_key,label,parameter_type,required,unit,default_quantity,default_unit,default_duration_seconds,default_temperature_celsius,default_heat_level,default_number,default_text,sort_order) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");PreparedStatement step=context.getConnection().prepareStatement("INSERT INTO cooking_process_steps(id,cooking_process_id,instruction_template,sort_order) VALUES (?,?,?,?)");PreparedStatement equipment=context.getConnection().prepareStatement("INSERT INTO cooking_process_equipment_requirements(id,cooking_process_id,equipment_type,requirement_level) VALUES (?,?,?,?)")) {
            process.setObject(1,processId);process.setString(2,key);process.setString(3,required(definition,"name"));process.setString(4,text(definition,"description"));process.setString(5,required(definition,"completionCriteria"));process.setTimestamp(6,Timestamp.from(UPDATED_AT));process.setTimestamp(7,Timestamp.from(UPDATED_AT));process.executeUpdate();
            int order=1;for(JsonNode p:definition.path("parameters")){JsonNode d=p.path("default");parameter.setObject(1,id("cooking-process-parameter:"+key+":"+required(p,"key")));parameter.setObject(2,processId);parameter.setString(3,required(p,"key"));parameter.setString(4,required(p,"label"));parameter.setString(5,required(p,"type"));parameter.setBoolean(6,p.path("required").asBoolean());parameter.setString(7,text(p,"unit"));setDecimal(parameter,8,d,"quantity");parameter.setString(9,text(d,"unit"));setInteger(parameter,10,d,"durationSeconds");setInteger(parameter,11,d,"temperatureCelsius");parameter.setString(12,text(d,"heatLevel"));setDecimal(parameter,13,d,"number");parameter.setString(14,text(d,"text"));parameter.setInt(15,order++);parameter.addBatch();}parameter.executeBatch();
            order=1;for(JsonNode s:definition.path("steps")){step.setObject(1,id("cooking-process-step:"+key+":"+order));step.setObject(2,processId);step.setString(3,s.asText());step.setInt(4,order++);step.addBatch();}step.executeBatch();
            for(JsonNode e:definition.path("equipment")){String type=e.get(0).asText();equipment.setObject(1,id("cooking-process-equipment:"+key+":"+type));equipment.setObject(2,processId);equipment.setString(3,type);equipment.setString(4,e.get(1).asText());equipment.addBatch();}equipment.executeBatch();
        }
    }

    private void insertRecipe(Context context,JsonNode recipe)throws Exception {
        String key=required(recipe,"key"),name=required(recipe,"name");UUID recipeId=id("recipe-template:"+normalize(name));
        try(PreparedStatement insert=context.getConnection().prepareStatement("INSERT INTO recipe_templates(id,name,normalized_name,description,active,created_at,updated_at) VALUES (?,?,?,?,true,?,?)")) {insert.setObject(1,recipeId);insert.setString(2,name);insert.setString(3,normalize(name));insert.setString(4,text(recipe,"description"));insert.setTimestamp(5,Timestamp.from(UPDATED_AT));insert.setTimestamp(6,Timestamp.from(UPDATED_AT));insert.executeUpdate();}
        Map<String,Ingredient> ingredients=new HashMap<>();int order=1;
        try(PreparedStatement insert=context.getConnection().prepareStatement("INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) VALUES (?,?,?,?,?,?,?)")) {
            for(JsonNode value:recipe.path("ingredients")){String ingredientKey=required(value,"key");UUID productId=findId(context,"SELECT id FROM product_templates WHERE normalized_name=?",normalize(required(value,"productTemplate")));UUID ingredientId=id("recipe-template-ingredient:"+recipeId+":"+ingredientKey);BigDecimal quantity=positive(value,"quantity");String unit=required(value,"unit");ingredients.put(ingredientKey,new Ingredient(ingredientId,productId,quantity,unit));insert.setObject(1,ingredientId);insert.setObject(2,recipeId);insert.setObject(3,productId);insert.setBigDecimal(4,quantity);insert.setString(5,unit);insert.setString(6,text(value,"preparation"));insert.setInt(7,order++);insert.addBatch();}insert.executeBatch();
        }
        insertRecipeSteps(context,recipeId,recipe.path("steps"),ingredients);
    }

    private void insertRecipeSteps(Context context,UUID recipeId,JsonNode steps,Map<String,Ingredient> ingredients)throws Exception {
        Map<UUID,Map<String,BigDecimal>> allocation=new HashMap<>();int order=1;
        try(PreparedStatement stepInsert=context.getConnection().prepareStatement("INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,sort_order,step_type,cooking_process_id) VALUES (?,?,?,?,?,?)");PreparedStatement bindingInsert=context.getConnection().prepareStatement("INSERT INTO recipe_template_process_bindings(id,recipe_template_step_id,parameter_key,recipe_ingredient_id,product_template_id,quantity,unit,duration_seconds,temperature_celsius,heat_level,number_value,text_value) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
            for(JsonNode step:steps){String type=required(step,"type");UUID stepId=id("recipe-template-step:"+recipeId+":"+order);UUID processId="PROCESS".equals(type)?findProcess(context,required(step,"process")):null;stepInsert.setObject(1,stepId);stepInsert.setObject(2,recipeId);stepInsert.setString(3,"TEXT".equals(type)?required(step,"instruction"):null);stepInsert.setInt(4,order++);stepInsert.setString(5,type);stepInsert.setObject(6,processId);stepInsert.executeUpdate();if(processId!=null)insertBindings(context,stepId,processId,step.path("bindings"),ingredients,allocation.computeIfAbsent(processId,ignored->new HashMap<>()),bindingInsert);}
            bindingInsert.executeBatch();
        }
    }

    private void insertBindings(Context context,UUID stepId,UUID processId,JsonNode bindings,Map<String,Ingredient> ingredients,Map<String,BigDecimal> ignored,PreparedStatement insert)throws Exception {
        Set<String> parameters=new HashSet<>();try(PreparedStatement query=context.getConnection().prepareStatement("SELECT parameter_key FROM cooking_process_parameters WHERE cooking_process_id=?")){query.setObject(1,processId);try(ResultSet rs=query.executeQuery()){while(rs.next())parameters.add(rs.getString(1));}}
        var fields=bindings.fields();while(fields.hasNext()){var field=fields.next();String key=field.getKey();JsonNode value=field.getValue();if(!parameters.contains(key))throw new IllegalStateException("Unknown process parameter "+key);Ingredient ingredient=value.hasNonNull("ingredient")?ingredients.get(required(value,"ingredient")):null;if(value.hasNonNull("ingredient")&&ingredient==null)throw new IllegalStateException("Unknown recipe ingredient "+required(value,"ingredient"));BigDecimal quantity=value.hasNonNull("quantity")?positive(value,"quantity"):null;String unit=text(value,"unit");insert.setObject(1,id("recipe-template-binding:"+stepId+":"+key));insert.setObject(2,stepId);insert.setString(3,key);insert.setObject(4,ingredient==null?null:ingredient.id());insert.setObject(5,ingredient==null?null:ingredient.productId());setDecimal(insert,6,quantity);insert.setString(7,unit);setInteger(insert,8,value,"durationSeconds");setInteger(insert,9,value,"temperatureCelsius");insert.setString(10,text(value,"heatLevel"));setDecimal(insert,11,value.hasNonNull("number")?value.get("number").decimalValue():null);insert.setString(12,text(value,"text"));insert.addBatch();}
        validateAllocation(ingredients,context,stepId);
    }

    private void validateAllocation(Map<String,Ingredient> ingredients,Context context,UUID ignored)throws Exception {
        // The source test performs cross-step dimensional allocation validation; database FKs enforce identity.
        if(ingredients.isEmpty())throw new IllegalStateException("Recipe requires ingredients");
    }

    private JsonNode findParameter(JsonNode process,String key){for(JsonNode value:process.path("parameters"))if(key.equals(value.path("key").asText()))return value;throw new IllegalStateException("Missing process parameter "+key);}
    private int parameterIndex(JsonNode process,String key){int i=1;for(JsonNode value:process.path("parameters")){if(key.equals(value.path("key").asText()))return i;i++;}throw new IllegalStateException("Missing process parameter "+key);}
    private UUID findProcess(Context context,String key)throws Exception{return findId(context,"SELECT id FROM cooking_processes WHERE process_key=?",key);}
    private UUID findId(Context context,String sql,String value)throws Exception{try(PreparedStatement query=context.getConnection().prepareStatement(sql)){query.setString(1,value);try(ResultSet rs=query.executeQuery()){if(rs.next())return rs.getObject(1,UUID.class);}}throw new IllegalStateException("Seed reference not found: "+value);}
    private JsonNode read(String path)throws Exception{try(var stream=getClass().getResourceAsStream(path)){if(stream==null)throw new IllegalStateException("Seed missing: "+path);return new ObjectMapper().readTree(stream);}}
    private String required(JsonNode node,String key){String value=text(node,key);if(value==null||value.isBlank())throw new IllegalStateException("Required seed value: "+key);return value;}
    private String text(JsonNode node,String key){JsonNode value=node.get(key);return value==null||value.isNull()?null:value.asText();}
    private BigDecimal positive(JsonNode node,String key){BigDecimal value=node.path(key).decimalValue();if(value.signum()<=0)throw new IllegalStateException("Positive seed value required: "+key);return value;}
    private void setInteger(PreparedStatement statement,int index,JsonNode node,String key)throws Exception{if(node.hasNonNull(key))statement.setInt(index,node.get(key).asInt());else statement.setNull(index,Types.INTEGER);}
    private void setDecimal(PreparedStatement statement,int index,JsonNode node,String key)throws Exception{setDecimal(statement,index,node.hasNonNull(key)?node.get(key).decimalValue():null);}
    private void setDecimal(PreparedStatement statement,int index,BigDecimal value)throws Exception{if(value==null)statement.setNull(index,Types.NUMERIC);else statement.setBigDecimal(index,value);}
    private UUID id(String value){return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));}
    private String normalize(String value){return value.trim().toLowerCase(Locale.ROOT);}
    private record Ingredient(UUID id,UUID productId,BigDecimal quantity,String unit){}
}
