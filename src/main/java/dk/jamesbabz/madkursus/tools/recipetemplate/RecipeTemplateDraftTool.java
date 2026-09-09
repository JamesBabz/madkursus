package dk.jamesbabz.madkursus.tools.recipetemplate;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import dk.jamesbabz.madkursus.service.models.RecipeUnit;
import dk.jamesbabz.madkursus.service.models.EquipmentType;
import dk.jamesbabz.madkursus.service.models.CookingProcessParameterType;
import dk.jamesbabz.madkursus.service.models.CookingProcessValue;
import dk.jamesbabz.madkursus.service.models.HeatLevel;
import dk.jamesbabz.madkursus.service.applications.CookingProcessBindingSemantics;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** File-only authoring tool. It deliberately never starts Spring or connects to a database. */
public final class RecipeTemplateDraftTool {
    public record Result(String key,String name,int ingredients,int components,int preparation,List<String> processes,List<String> warnings,Path canonical,Path migration,boolean update) {}
    public enum Action { ADD, UPDATE }
    public record ProcessResult(Result validation,Action action,String existingName,List<String> warnings,Path migration,boolean dryRun) {}
    private record Parameter(String key,String type,boolean required,String source,boolean overrideable,boolean hasDefault,String unit) {}
    private record Process(Map<String,Parameter> parameters) {}
    private record Allocation(String ingredient,BigDecimal quantity,String unit,String owner) {}
    public record ValidationError(String path,String message) {}
    public static final class ValidationException extends IllegalArgumentException {
        private final List<ValidationError> errors;
        public ValidationException(List<ValidationError> errors) {
            super(errors.stream().map(e -> e.path()+": "+e.message()).collect(java.util.stream.Collectors.joining("\n")));
            this.errors=List.copyOf(errors);
        }
        public List<ValidationError> errors() {return errors;}
    }
    private final IdentityHashMap<JsonNode,String> locations=new IdentityHashMap<>();
    private String location="$";
    private static final ObjectMapper JSON=new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static final Pattern KEY=Pattern.compile("[A-Z][A-Z0-9_]*");
    private final Path project;
    private final Path canonical;
    private final Map<String,JsonNode> products;
    private final Map<String,Process> processes;
    private final Set<String> canonicalKeys;
    private final Map<String,JsonNode> canonicalRecipes;

    public RecipeTemplateDraftTool(Path project)throws IOException {
        this.project=project.toAbsolutePath().normalize();canonical=this.project.resolve("src/main/resources/seed/recipe-templates.json");
        products=catalog(this.project.resolve("src/main/resources/seed/product-templates.json"),"products");
        processes=processCatalog(this.project.resolve("src/main/resources/seed/cooking-processes.json"));
        canonicalRecipes=keyed(JSON.readTree(canonical.toFile()).path("recipes"));canonicalKeys=canonicalRecipes.keySet();
    }

    public Result validate(Path draft)throws IOException {return inspect(draft,false,false,null);}
    public Result importDraft(Path draft,boolean update,Path migrationDirectory)throws IOException {return inspect(draft,true,update,migrationDirectory);}
    public ProcessResult processDraft(Path draft,boolean dryRun,Path migrationDirectory)throws IOException {
        Result validation=validate(draft);Action action=validation.update()?Action.UPDATE:Action.ADD;JsonNode existing=canonicalRecipes.get(validation.key());List<String>warnings=new ArrayList<>();
        if(action==Action.ADD)canonicalRecipes.forEach((key,value)->{if(similarName(required(value,"name"),validation.name()))warnings.add("A RecipeTemplate with a similar name already exists: "+key+" - "+required(value,"name")+". Draft key: "+validation.key()+". No automatic update performed based on name.");});
        Path directory=migrationDirectory==null?project.resolve("build/generated-recipe-template-migrations"):migrationDirectory;Path candidate=directory.resolve("V_NEXT__"+action.name().toLowerCase(Locale.ROOT)+"_"+validation.key().toLowerCase(Locale.ROOT)+".sql");
        if(dryRun)return new ProcessResult(validation,action,existing==null?null:required(existing,"name"),List.copyOf(warnings),candidate,true);
        Result imported=importDraft(draft,action==Action.UPDATE,directory);return new ProcessResult(imported,action,existing==null?null:required(existing,"name"),List.copyOf(warnings),imported.migration(),false);
    }
    public ProcessResult regenerateAddMigration(Path draft,Path migrationDirectory)throws IOException {
        Result validation=validate(draft);JsonNode existing=canonicalRecipes.get(validation.key());if(existing==null)fail("Recovery ADD requires an existing canonical RecipeTemplate key: "+validation.key());
        JsonNode parsed=JSON.readTree(draft.toFile());ObjectNode authored=object(parsed.has("recipe")?parsed.get("recipe"):parsed,"Recipe draft must be a JSON object").deepCopy();ObjectNode canonicalRecipe=object(existing.deepCopy(),"Canonical RecipeTemplate must be an object");authored.remove("id");ObjectNode comparable=canonicalRecipe.deepCopy();comparable.remove("id");if(!authored.equals(comparable))fail("Recovery ADD draft differs from the canonical RecipeTemplate; process the canonical update first");
        String identity=canonicalRecipe.hasNonNull("id")?canonicalRecipe.get("id").asText():uuid("recipe-template:"+normalize(required(canonicalRecipe,"name"))).toString();validateTemplateId(validation.key(),identity);canonicalRecipe.put("id",identity);String sql=migration(canonicalRecipe,true);Path directory=migrationDirectory==null?project.resolve("build/generated-recipe-template-migrations"):migrationDirectory;Path candidate=directory.resolve("V_NEXT__add_"+validation.key().toLowerCase(Locale.ROOT)+".sql");Files.createDirectories(directory);Files.writeString(candidate,sql,StandardCharsets.UTF_8);return new ProcessResult(validation,Action.ADD,required(existing,"name"),List.of("Recovery mode: canonical source was not modified; any legacy child graph under the stable parent ID is replaced."),candidate,false);
    }

    private Result inspect(Path draft,boolean write,boolean update,Path migrationDirectory)throws IOException {
        return inspect(JSON.readTree(draft.toFile()),write,update,migrationDirectory);
    }

    private Result inspect(JsonNode parsed,boolean write,boolean update,Path migrationDirectory)throws IOException {
        locations.clear();indexLocations(parsed,"$");location="$";
        preflight(parsed);
        ObjectNode recipe=object(parsed!=null&&parsed.has("recipe")?parsed.get("recipe"):parsed,"Recipe draft must be a JSON object");
        String key=requiredKey(recipe,"key"),name=required(recipe,"name");boolean exists=canonicalKeys.contains(key);
        if(write&&exists&&!update)fail("RecipeTemplate key already exists; rerun import with --update: "+key);
        if(write&&!exists&&update)fail("Cannot update unknown RecipeTemplate key: "+key);
        ArrayNode ingredients=array(recipe,"ingredients",true);Map<String,JsonNode> ingredientByKey=new LinkedHashMap<>();
        for(JsonNode value:ingredients){locate(value);String ingredientKey=requiredKey(value,"key");if(ingredientByKey.putIfAbsent(ingredientKey,value)!=null)fail("Duplicate ingredient key: "+ingredientKey);String product=required(value,"productTemplate");if(!products.containsKey(product))fail("Unknown ProductTemplate key/name: "+product);positive(value,"quantity");unit(value,"unit");}
        List<Allocation> allocations=new ArrayList<>();Set<String> componentKeys=new HashSet<>();ArrayNode components=array(recipe,"preparedComponents",false);int componentOrder=1;
        for(JsonNode component:components){locate(component);String componentKey=requiredKey(component,"key");if(!componentKeys.add(componentKey))fail("Duplicate PreparedComponent key: "+componentKey);required(component,"name");int order=component.path("sortOrder").asInt(componentOrder++);locateField(component,"sortOrder");if(order<=0)fail("PreparedComponent sortOrder must be positive: "+componentKey);for(JsonNode allocation:array(component,"ingredients",true)){locate(allocation);String ingredient=required(allocation,"ingredient");if(!ingredientByKey.containsKey(ingredient))fail("PreparedComponent "+componentKey+" references unknown ingredient: "+ingredient);allocations.add(new Allocation(ingredient,positive(allocation,"quantity"),unit(allocation,"unit"),path(allocation)));}for(JsonNode prep:array(component,"preparation",false))instruction(prep,instructionPath(prep));}
        for(JsonNode prep:array(recipe,"preparation",false)){instruction(prep,instructionPath(prep));if(prep.isObject()&&prep.hasNonNull("component")&&!componentKeys.contains(prep.get("component").asText())){locateField(prep,"component");fail("Preparation references unknown PreparedComponent: "+prep.get("component").asText());}}
        for(JsonNode component:components)for(JsonNode prep:array(component,"preparation",false))validateInstruction(prep.isObject()&&prep.has("instruction")?prep.get("instruction"):prep,instructionPath(prep),ingredientByKey,componentKeys);
        for(JsonNode prep:array(recipe,"preparation",false))validateInstruction(prep.isObject()&&prep.has("instruction")?prep.get("instruction"):prep,instructionPath(prep),ingredientByKey,componentKeys);
        ArrayNode steps=array(recipe,"steps",true);List<String> usedProcesses=new ArrayList<>();int prepCount=array(recipe,"preparation",false).size();
        for(JsonNode step:steps)if("TEXT".equals(step.path("type").asText())&&step.has("instruction"))validateInstruction(step.get("instruction"),child(path(step),"instruction"),ingredientByKey,componentKeys);
        for(JsonNode step:steps){locate(step);String type=required(step,"type");if(type.equals("TEXT")){required(step,"instruction");continue;}if(!type.equals("PROCESS"))fail("Unknown step type: "+type);String processKey=required(step,"process");Process process=processes.get(processKey);if(process==null)fail("Unknown CookingProcess key: "+processKey);usedProcesses.add(processKey);ObjectNode bindings=object(step.path("bindings"),"PROCESS bindings must be an object: "+processKey);Set<String> supplied=new HashSet<>();Iterator<Map.Entry<String,JsonNode>> fields=bindings.fields();while(fields.hasNext()){var field=fields.next();locateField(bindings,field.getKey());Parameter parameter=process.parameters.get(field.getKey());if(parameter==null)fail("Unknown parameter "+field.getKey()+" for process "+processKey);supplied.add(field.getKey());JsonNode value=field.getValue();locate(value);if(value.hasNonNull("component")){String component=value.get("component").asText();if(!componentKeys.contains(component)){locateField(value,"component");fail("Process "+processKey+" references unknown PreparedComponent: "+component);}validateBindingSemantics(value,parameter,processKey,false,false,true);continue;}if(value.hasNonNull("ingredient")){String ingredient=required(value,"ingredient");if(!ingredientByKey.containsKey(ingredient))fail("Process "+processKey+" references unknown ingredient: "+ingredient);allocations.add(new Allocation(ingredient,positive(value,"quantity"),unit(value,"unit"),path(value)));validateBindingSemantics(value,parameter,processKey,true,true,false);continue;}if(!"INPUT".equals(inputSource(parameter))&&!parameter.overrideable&&!"OVERRIDEABLE_DEFAULT".equals(parameter.source))fail("Parameter "+field.getKey()+" is not overrideable for process "+processKey);validateValue(value,parameter,processKey);}
            locate(bindings);for(Parameter parameter:process.parameters.values())if(parameter.required&&"INPUT".equals(inputSource(parameter))&&!supplied.contains(parameter.key)&&!(processKey.equals("MIX_MEATBALL_MIXTURE")&&parameter.key.equals("BASE")&&supplied.contains("MEAT"))){locateField(bindings,parameter.key);fail("Missing required process input "+parameter.key+" for "+processKey);}
        }
        validateAllocations(ingredientByKey,allocations);validateEquipment(recipe.path("equipmentRequirements"),child(path(recipe),"equipmentRequirements"));
        Result result=new Result(key,name,ingredients.size(),components.size(),prepCount,List.copyOf(new LinkedHashSet<>(usedProcesses)),List.of(),canonical,null,exists);
        if(!write)return result;
        var prepared=prepareRecipe(recipe,result);
        Path migrationDir=migrationDirectory==null?project.resolve("build/generated-recipe-template-migrations"):migrationDirectory;
        Path migration=migrationDir.resolve("V_NEXT__"+(exists?"update_":"add_")+key.toLowerCase(Locale.ROOT)+".sql");
        Files.createDirectories(migrationDir);Files.writeString(canonical,prepared.after(),StandardCharsets.UTF_8);Files.writeString(migration,prepared.sql(),StandardCharsets.UTF_8);
        return new Result(key,name,ingredients.size(),components.size(),prepCount,List.copyOf(new LinkedHashSet<>(usedProcesses)),List.of(),canonical,migration,exists);
    }

    public record PreparedDraft(Result validation,String before,String after,String sql,int textSteps,int processSteps,List<String> warnings) {}
    /** Shared validation and generation, entirely in memory: safe for validate-only HTTP and CLI callers. */
    public PreparedDraft prepare(String draft)throws IOException {
        JsonNode parsed=JSON.readTree(draft);
        Result result=inspect(parsed,false,false,null);
        return prepareRecipe(object(parsed.has("recipe")?parsed.get("recipe"):parsed,"Recipe draft must be an object"),result);
    }
    private PreparedDraft prepareRecipe(ObjectNode authored,Result result)throws IOException {
        ObjectNode recipe=authored.deepCopy();String key=result.key(),name=result.name();boolean exists=result.update();
        String identity=exists&&canonicalRecipes.get(key).hasNonNull("id")?canonicalRecipes.get(key).get("id").asText():uuid("recipe-template:"+(exists?required(canonicalRecipes.get(key),"name"):name).trim().toLowerCase(Locale.ROOT)).toString();
        locateField(authored,"name");validateTemplateId(key,identity);recipe.put("id",identity);
        String before=Files.readString(canonical,StandardCharsets.UTF_8);
        String after=replaceRecipe(before,key,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(recipe),exists);
        List<String> warnings=new ArrayList<>();
        if(!exists)canonicalRecipes.forEach((k,v)->{if(similarName(required(v,"name"),name))warnings.add("Similar name already exists: "+k+" - "+required(v,"name")+". Stable key selects ADD, not UPDATE.");});
        int text=0,process=0;for(var step:recipe.path("steps")){if("TEXT".equals(step.path("type").asText()))text++;else process++;}
        return new PreparedDraft(result,before,after,migration(recipe,exists),text,process,List.copyOf(warnings));
    }

    private void validateAllocations(Map<String,JsonNode> ingredients,List<Allocation> values) {
        Map<String,BigDecimal> sums=new HashMap<>();
        for(Allocation value:values) {
            JsonNode ingredient=ingredients.get(value.ingredient);String totalUnit=required(ingredient,"unit");
            location=value.owner+".unit";
            if(!dimension(totalUnit).equals(dimension(value.unit)))fail("Incompatible allocation unit for "+value.ingredient);
            BigDecimal sum=sums.merge(value.ingredient,base(value.quantity,value.unit),BigDecimal::add);
            location=value.owner+".quantity";
            if(sum.compareTo(base(ingredient.get("quantity").decimalValue(),totalUnit))>0)
                fail("Allocations exceed ingredient quantity: "+value.ingredient);
        }
    }
    private void validateValue(JsonNode value,Parameter p,String process){validateBindingSemantics(value,p,process,false,false,false);}

    private void validateBindingSemantics(JsonNode value,Parameter p,String process,boolean ingredient,boolean product,boolean component){
        locate(value);
        try{
            CookingProcessValue runtimeValue=new CookingProcessValue(decimal(value,"quantity"),enumValue(RecipeUnit.class,value,"unit"),integer(value,"durationSeconds"),integer(value,"temperatureCelsius"),enumValue(HeatLevel.class,value,"heatLevel"),decimal(value,"number"),text(value,"text"));
            CookingProcessBindingSemantics.validate(CookingProcessParameterType.valueOf(p.type),p.key,ingredient,product,component,runtimeValue,p.unit==null?null:RecipeUnit.valueOf(p.unit));
        }catch(dk.jamesbabz.madkursus.service.exceptions.InvalidInputException e){
            String field=switch(CookingProcessParameterType.valueOf(p.type)) {
                case INGREDIENT_QUANTITY, QUANTITY -> "quantity";
                case DURATION -> "durationSeconds";
                case TEMPERATURE -> "temperatureCelsius";
                case HEAT_LEVEL -> "heatLevel";
                case NUMBER -> "number";
                case TEXT -> "text";
                case INGREDIENT_LIST -> "ingredient";
            };
            locateField(value,field);fail("Invalid binding for "+p.key+" in "+process+": "+e.getMessage());
        }catch(ValidationException e){throw e;}
        catch(IllegalArgumentException e){fail("Invalid binding for "+p.key+" in "+process+": "+e.getMessage());}
    }
    private void validateEquipment(JsonNode value,String owner) {
        location=owner;if(value.isMissingNode())return;
        if(!value.isArray())fail("equipmentRequirements must be an array");
        int index=0;
        for(JsonNode item:value) {
            String at=owner+"["+(index++)+"]";location=at;
            if(!(item.isTextual()||(item.isObject()&&(item.hasNonNull("equipmentType")||item.hasNonNull("label")))))fail("Invalid equipment requirement");
            String type=item.isTextual()?item.asText():item.path("equipmentType").asText(null);
            if(item.isObject())location=child(at,"equipmentType");
            if(type!=null)try {EquipmentType.valueOf(type);}catch(IllegalArgumentException e) {fail("Unknown equipment type: "+type);}
        }
    }

    private String migration(ObjectNode recipe,boolean update)throws IOException {String key=required(recipe,"key"),id=required(recipe,"id");StringBuilder s=new StringBuilder("-- REVIEW CANDIDATE: rename V_NEXT before deployment. Generated deterministically.\n");s.append("-- Existing copied Recipes are intentionally untouched.\nBEGIN;\n");if(update)s.append("DELETE FROM recipe_template_process_bindings WHERE recipe_template_step_id IN (SELECT id FROM recipe_template_steps WHERE recipe_template_id='").append(id).append("');\nDELETE FROM recipe_template_prepared_component_ingredients WHERE prepared_component_id IN (SELECT id FROM recipe_template_prepared_components WHERE recipe_template_id='").append(id).append("');\nDELETE FROM recipe_template_preparation_steps WHERE recipe_template_id='").append(id).append("';\nDELETE FROM recipe_template_steps WHERE recipe_template_id='").append(id).append("';\nDELETE FROM recipe_template_prepared_components WHERE recipe_template_id='").append(id).append("';\nDELETE FROM recipe_template_ingredients WHERE recipe_template_id='").append(id).append("';\n");s.append("INSERT INTO recipe_templates(id,name,normalized_name,description,active,created_at,updated_at) VALUES ('").append(id).append("','").append(sql(required(recipe,"name"))).append("','").append(sql(required(recipe,"name").toLowerCase(Locale.ROOT))).append("',").append(nullable(recipe,"description")).append(",true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON CONFLICT(id) DO UPDATE SET name=EXCLUDED.name,normalized_name=EXCLUDED.normalized_name,description=EXCLUDED.description,active=true,updated_at=CURRENT_TIMESTAMP;\n");
        if(update)s.append("DELETE FROM recipe_template_equipment_requirements WHERE recipe_template_id='").append(id).append("';\n");String child="recipe-template-child:"+key+":";int equipmentOrder=1;for(JsonNode equipment:array(recipe,"equipmentRequirements",false)){String type=equipment.isTextual()?equipment.asText():equipment.path("equipmentType").asText(null),label=equipment.isObject()?equipment.path("label").asText(null):null;s.append("INSERT INTO recipe_template_equipment_requirements(id,recipe_template_id,equipment_type,label,sort_order) VALUES ('").append(uuid(child+"equipment:"+equipmentOrder)).append("','").append(id).append("',").append(type==null?"NULL":"'"+sql(type)+"'").append(',').append(label==null?"NULL":"'"+sql(label)+"'").append(',').append(equipmentOrder++).append(");\n");}
        Map<String,String> ingredientIds=new LinkedHashMap<>(),ingredientProductIds=new LinkedHashMap<>();int order=1;for(JsonNode i:array(recipe,"ingredients",true)){String ik=required(i,"key"),iid=uuid(child+"ingredient:"+ik).toString(),productId=requiredProductId(i);ingredientIds.put(ik,iid);ingredientProductIds.put(ik,productId);s.append("INSERT INTO recipe_template_ingredients(id,recipe_template_id,product_template_id,quantity,unit,preparation,sort_order) SELECT '").append(iid).append("','").append(id).append("',id,").append(i.get("quantity")).append(",'").append(required(i,"unit")).append("',").append(nullable(i,"preparation")).append(',').append(order++).append(" FROM product_templates WHERE id='").append(productId).append("';\n");}
        Map<String,String> componentIds=new HashMap<>();order=1;for(JsonNode c:array(recipe,"preparedComponents",false)){String ck=required(c,"key"),cid=uuid(child+"component:"+ck).toString();componentIds.put(ck,cid);s.append("INSERT INTO recipe_template_prepared_components(id,recipe_template_id,component_key,name,sort_order) VALUES ('").append(cid).append("','").append(id).append("','").append(ck).append("','").append(sql(required(c,"name"))).append("',").append(c.path("sortOrder").asInt(order++)).append(");\n");int aorder=1;for(JsonNode a:array(c,"ingredients",true)){String aid=uuid(child+"component-allocation:"+ck+":"+aorder).toString();s.append("INSERT INTO recipe_template_prepared_component_ingredients(id,prepared_component_id,recipe_ingredient_id,quantity,unit,sort_order) VALUES ('").append(aid).append("','").append(cid).append("','").append(ingredientIds.get(required(a,"ingredient"))).append("',").append(a.get("quantity")).append(",'").append(required(a,"unit")).append("',").append(aorder++).append(");\n");}int porder=1;for(JsonNode p:array(c,"preparation",false)){JsonNode authored=p.isObject()&&p.has("instruction")?p.get("instruction"):p;s.append("INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('").append(uuid(child+"component-preparation:"+ck+":"+porder)).append("','").append(id).append("','").append(cid).append("',").append(authored.isTextual()?"'"+sql(authored.asText())+"'":"'[structured instruction]'").append(',').append(structuredSql(authored,ingredientIds,componentIds)).append(',').append(porder++).append(");\n");}}
        int prepOrder=1;for(JsonNode p:array(recipe,"preparation",false)){JsonNode authored=p.isObject()&&p.has("instruction")?p.get("instruction"):p;String component=p.isObject()&&p.hasNonNull("component")?"'"+componentIds.get(p.get("component").asText())+"'":"NULL";s.append("INSERT INTO recipe_template_preparation_steps(id,recipe_template_id,prepared_component_id,instruction,structured_instruction,sort_order) VALUES ('").append(uuid(child+"preparation:"+prepOrder)).append("','").append(id).append("',").append(component).append(',').append(authored.isTextual()?"'"+sql(authored.asText())+"'":"'[structured instruction]'").append(',').append(structuredSql(authored,ingredientIds,componentIds)).append(',').append(prepOrder++).append(");\n");}
        order=1;for(JsonNode step:array(recipe,"steps",true)){String sid=uuid(child+"step:"+order).toString(),type=required(step,"type");JsonNode authored=step.path("instruction");String legacyInstruction=type.equals("TEXT")?(authored.isTextual()?"'"+sql(authored.asText())+"'":"'[structured instruction]'"):"NULL";s.append("INSERT INTO recipe_template_steps(id,recipe_template_id,instruction,structured_instruction,sort_order,step_type,cooking_process_id) VALUES ('").append(sid).append("','").append(id).append("',").append(legacyInstruction).append(',').append(type.equals("TEXT")?structuredSql(authored,ingredientIds,componentIds):"NULL").append(',').append(order).append(",'").append(type).append("',").append(type.equals("PROCESS")?"(SELECT id FROM cooking_processes WHERE process_key='"+required(step,"process")+"')":"NULL").append(");\n");if(type.equals("PROCESS")){Iterator<Map.Entry<String,JsonNode>> it=object(step.path("bindings"),"bindings").fields();while(it.hasNext()){var b=it.next();JsonNode v=b.getValue();String ingredientKey=v.hasNonNull("ingredient")?v.get("ingredient").asText():null;String bid=uuid(child+"binding:"+order+":"+b.getKey()).toString();s.append("INSERT INTO recipe_template_process_bindings(id,recipe_template_step_id,parameter_key,recipe_ingredient_id,product_template_id,prepared_component_id,quantity,unit,duration_seconds,temperature_celsius,heat_level,number_value,text_value) VALUES ('").append(bid).append("','").append(sid).append("','").append(b.getKey()).append("',").append(ingredientKey==null?"NULL":"'"+ingredientIds.get(ingredientKey)+"'").append(',').append(ingredientKey==null?"NULL":"'"+ingredientProductIds.get(ingredientKey)+"'").append(',').append(v.hasNonNull("component")?"'"+componentIds.get(v.get("component").asText())+"'":"NULL").append(',').append(numberOrNull(v,"quantity")).append(',').append(quotedOrNull(v,"unit")).append(',').append(numberOrNull(v,"durationSeconds")).append(',').append(numberOrNull(v,"temperatureCelsius")).append(',').append(quotedOrNull(v,"heatLevel")).append(',').append(numberOrNull(v,"number")).append(',').append(quotedOrNull(v,"text")).append(");\n");}}order++;}return s.append("COMMIT;\n").toString();}

    private String replaceRecipe(String source,String key,String replacement,boolean exists){int recipes=source.indexOf("\"recipes\"");int start=source.indexOf('[',recipes),end=matching(source,start,'[',']');if(!exists)return source.substring(0,end).stripTrailing()+",\n"+indent(replacement,4)+"\n  "+source.substring(end);int cursor=start+1;while(cursor<end){while(cursor<end&&(Character.isWhitespace(source.charAt(cursor))||source.charAt(cursor)==','))cursor++;if(source.charAt(cursor)!='{')break;int close=matching(source,cursor,'{','}');try{JsonNode node=JSON.readTree(source.substring(cursor,close+1));if(key.equals(node.path("key").asText()))return source.substring(0,cursor)+indent(replacement,4)+source.substring(close+1);}catch(IOException e){throw new IllegalStateException(e);}cursor=close+1;}fail("Existing canonical RecipeTemplate could not be located: "+key);return source;}
    private String structuredSql(JsonNode authored,Map<String,String> ingredients,Map<String,String> components){if(!authored.isObject())return "NULL";ObjectNode value=JSON.createObjectNode();ArrayNode parts=value.putArray("parts");for(JsonNode part:authored.path("parts")){ObjectNode target=parts.addObject();if(part.hasNonNull("text"))target.put("text",part.get("text").asText());if(part.hasNonNull("ingredient"))target.put("recipeIngredientId",ingredients.get(part.get("ingredient").asText()));if(part.hasNonNull("component"))target.put("preparedComponentId",components.get(part.get("component").asText()));if(part.hasNonNull("quantity"))target.set("quantity",part.get("quantity"));if(part.hasNonNull("unit"))target.put("unit",part.get("unit").asText());if(part.hasNonNull("scaledNumber"))target.set("scaledNumber",part.get("scaledNumber"));}try{return "'"+sql(JSON.writeValueAsString(value))+"'::jsonb";}catch(IOException e){throw new IllegalStateException(e);}}
    private int matching(String text,int start,char open,char close){boolean string=false,escape=false;int depth=0;for(int i=start;i<text.length();i++){char c=text.charAt(i);if(string){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='\"')string=false;}else if(c=='\"')string=true;else if(c==open)depth++;else if(c==close&&--depth==0)return i;}fail("Malformed canonical JSON");return -1;}
    private String indent(String value,int spaces){String prefix=" ".repeat(spaces);return prefix+value.replace("\n","\n"+prefix);}
    private Map<String,JsonNode> catalog(Path path,String array)throws IOException{JsonNode root=JSON.readTree(path.toFile());Map<String,JsonNode> out=new LinkedHashMap<>();for(JsonNode n:root.path(array)){String key=required(n,"key");out.put(key,n);if(n.hasNonNull("name"))out.putIfAbsent(n.get("name").asText(),n);}return out;}
    private Map<String,JsonNode> keyed(JsonNode values){Map<String,JsonNode> out=new LinkedHashMap<>();for(JsonNode value:values)out.put(required(value,"key"),value);return out;}
    private Map<String,Process> processCatalog(Path path)throws IOException{Map<String,Process> out=new HashMap<>();for(JsonNode p:JSON.readTree(path.toFile()).path("processes")){Map<String,Parameter> params=new LinkedHashMap<>();for(JsonNode v:p.path("parameters")){String source=v.path("source").asText(v.path("default").isObject()?"DEFAULT":"INPUT"),type=required(v,"type");if(type.equals("QUANTITY")&&v.hasNonNull("inventory"))type="INGREDIENT_QUANTITY";params.put(required(v,"key"),new Parameter(required(v,"key"),type,v.path("required").asBoolean(),source,v.path("overrideable").asBoolean(),v.path("default").isObject(),v.hasNonNull("unit")?v.get("unit").asText():null));}out.put(required(p,"key"),new Process(params));}return out;}
    private String requiredProductId(JsonNode ingredient){JsonNode p=products.get(required(ingredient,"productTemplate"));return required(p,"id");}
    private String inputSource(Parameter p){return p.source==null||p.source.isBlank()?(p.hasDefault?"DEFAULT":"INPUT"):p.source;}
    private ArrayNode array(JsonNode parent,String key,boolean required){locateField(parent,key);JsonNode value=parent.path(key);if(value.isMissingNode()&&!required)return JSON.createArrayNode();if(!value.isArray()||(required&&value.isEmpty()))fail(key+" must be a non-empty array");return (ArrayNode)value;}
    private ObjectNode object(JsonNode value,String message){locate(value);if(value==null||!value.isObject())fail(message);return (ObjectNode)value;}
    private String required(JsonNode value,String key){locateField(value,key);JsonNode node=value.path(key);if(key.equals("instruction")&&node.isObject()&&node.path("parts").isArray())return "[structured instruction]";String text=node.asText();if(!node.isTextual()||text.isBlank())fail(key+" is required");return text;}
    private String requiredKey(JsonNode value,String key){String text=required(value,key);if(!KEY.matcher(text).matches())fail(key+" must be a stable uppercase key: "+text);return text;}
    private BigDecimal positive(JsonNode value,String key){locateField(value,key);if(!value.has(key)||!value.get(key).isNumber()||value.get(key).decimalValue().signum()<=0)fail(key+" must be greater than zero");return value.get(key).decimalValue();}
    private String unit(JsonNode value,String key){String unit=required(value,key);try{RecipeUnit.valueOf(unit);}catch(Exception e){fail("Unknown RecipeUnit: "+unit);}return unit;}
    private void instruction(JsonNode value,String owner){location=owner;JsonNode instruction=value.isObject()&&value.has("instruction")?value.get("instruction"):value;if(instruction.isObject()&&instruction.path("parts").isArray())return;String text=instruction.asText();if(text.isBlank())fail(owner+" instruction is required");}
    private String instructionPath(JsonNode prep) {return prep.isObject()&&prep.has("instruction")?child(path(prep),"instruction"):path(prep);}
    private void validateInstruction(JsonNode value,String owner,Map<String,JsonNode> ingredients,Set<String> components) {
        location=owner;
        if(value==null||value.isNull())fail("instruction is required");
        if(value.isTextual()) {if(value.asText().isBlank())fail("instruction is required");return;}
        if(!value.isObject())fail("instruction must be plain text or structured parts");
        location=child(owner,"parts");
        if(!value.path("parts").isArray()||value.path("parts").isEmpty())fail("structured instruction requires non-empty parts");
        location=owner;
        if(value.size()!=1)fail("instruction must be either plain text or structured parts, not both");
        int index=0;
        for(JsonNode part:value.path("parts")) {
            String partPath=child(owner,"parts")+"["+(index++)+"]";location=partPath;
            if(!part.isObject())fail("instruction part must be an object");
            int kinds=(part.hasNonNull("text")?1:0)+(part.hasNonNull("ingredient")?1:0)+(part.hasNonNull("component")?1:0)+(part.hasNonNull("scaledNumber")?1:0);
            if(kinds!=1)fail("instruction part must contain exactly one supported part type");
            if(part.hasNonNull("ingredient")) {
                String key=part.get("ingredient").asText();JsonNode ingredient=ingredients.get(key);
                location=child(partPath,"ingredient");if(ingredient==null)fail("references unknown ingredient: "+key);
                if(part.hasNonNull("quantity")!=part.hasNonNull("unit")) {
                    location=child(partPath,part.hasNonNull("quantity")?"unit":"quantity");
                    fail("partial ingredient reference requires both quantity and unit");
                }
                if(part.hasNonNull("quantity")) {
                    BigDecimal amount=positive(part,"quantity");String unit=unit(part,"unit"),totalUnit=required(ingredient,"unit");
                    location=child(partPath,"unit");
                    if(!dimension(unit).equals(dimension(totalUnit)))fail("partial ingredient unit is incompatible: "+key);
                    location=child(partPath,"quantity");
                    if(base(amount,unit).compareTo(base(ingredient.get("quantity").decimalValue(),totalUnit))>0)fail("partial ingredient reference exceeds ingredient quantity: "+key);
                }
            }
            location=child(partPath,"component");
            if(part.hasNonNull("component")&&!components.contains(part.get("component").asText()))fail("references unknown PreparedComponent: "+part.get("component").asText());
            if(part.hasNonNull("scaledNumber"))positive(part,"scaledNumber");
        }
    }
    private String dimension(String unit){return switch(RecipeUnit.valueOf(unit)){case GRAM->"MASS";case PIECE->"COUNT";case MILLILITER,DECILITER,TEASPOON,TABLESPOON->"VOLUME";case GRINDER_TURN->"GRINDER";};}
    private BigDecimal base(BigDecimal value,String unit){return value.multiply(switch(RecipeUnit.valueOf(unit)){case GRAM,MILLILITER,PIECE,GRINDER_TURN->BigDecimal.ONE;case DECILITER->new BigDecimal("100");case TEASPOON->new BigDecimal("5");case TABLESPOON->new BigDecimal("15");});}
    private void validateTemplateId(String targetKey,String identity){for(var entry:canonicalRecipes.entrySet()){if(entry.getKey().equals(targetKey))continue;JsonNode value=entry.getValue();String other=value.hasNonNull("id")?value.get("id").asText():uuid("recipe-template:"+normalize(required(value,"name"))).toString();if(identity.equals(other))fail("RecipeTemplate ID collision with key "+entry.getKey()+": "+identity);}}
    private String normalize(String value){return value.trim().toLowerCase(Locale.ROOT);}
    private boolean similarName(String left,String right){String a=normalize(left),b=normalize(right);return a.equals(b)||(Math.min(a.length(),b.length())>=5&&(a.contains(b)||b.contains(a)));}
    private UUID uuid(String value){if(value.startsWith("recipe-template:"))for(JsonNode recipe:canonicalRecipes.values())if(recipe.hasNonNull("id")&&value.equals("recipe-template:"+recipe.path("name").asText().trim().toLowerCase(Locale.ROOT)))return UUID.fromString(recipe.get("id").asText());return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));}
    private String sql(String value){return value.replace("'","''");}private String nullable(JsonNode n,String k){return n.hasNonNull(k)?"'"+sql(n.get(k).asText())+"'":"NULL";}private String numberOrNull(JsonNode n,String k){return n.hasNonNull(k)?n.get(k).asText():"NULL";}private String quotedOrNull(JsonNode n,String k){return n.hasNonNull(k)?"'"+sql(n.get(k).asText())+"'":"NULL";}
    private BigDecimal decimal(JsonNode n,String k){return n.hasNonNull(k)&&n.get(k).isNumber()?n.get(k).decimalValue():null;}
    private Integer integer(JsonNode n,String k){return n.hasNonNull(k)&&n.get(k).isIntegralNumber()?n.get(k).intValue():null;}
    private String text(JsonNode n,String k){return n.hasNonNull(k)&&n.get(k).isTextual()?n.get(k).asText():null;}
    private <E extends Enum<E>> E enumValue(Class<E> type,JsonNode n,String k){String value=text(n,k);if(value==null)return null;try{return Enum.valueOf(type,value);}catch(Exception e){locateField(n,k);fail("Unknown "+type.getSimpleName()+": "+value);return null;}}
    private void fail(String message){throw new ValidationException(List.of(new ValidationError(location,message)));}

    private String path(JsonNode node) {return locations.getOrDefault(node,"$");}
    private void locate(JsonNode node) {location=path(node);}
    private void locateField(JsonNode node,String field) {location=child(path(node),field);}
    private String child(String parent,String field) {
        String suffix=field.matches("[A-Za-z_][A-Za-z0-9_]*")?field:"['"+field.replace("\\","\\\\").replace("'","\\'")+"']";
        return parent.equals("$")?(suffix.startsWith("[")?"$"+suffix:suffix):parent+(suffix.startsWith("[")?"":".")+suffix;
    }
    private void indexLocations(JsonNode node,String path) {
        if(node==null)return;
        // Container identity is unique; scalar singleton nodes (null, booleans) are not.
        if(node.isContainerNode())locations.put(node,path);
        if(node.isArray())for(int i=0;i<node.size();i++)indexLocations(node.get(i),path+"["+i+"]");
        if(node.isObject())node.fields().forEachRemaining(e->indexLocations(e.getValue(),child(path,e.getKey())));
    }
    /** Collect independent primitive errors before dependent reference/allocation validation. */
    private void preflight(JsonNode parsed) {
        JsonNode recipe=parsed!=null&&parsed.has("recipe")?parsed.get("recipe"):parsed;
        if(recipe==null||!recipe.isObject())return;
        List<ValidationError> errors=new ArrayList<>();
        checkObjectArray(recipe,"ingredients",true,errors);
        checkObjectArray(recipe,"preparedComponents",false,errors);
        checkObjectArray(recipe,"steps",true,errors);
        for(JsonNode component:recipe.path("preparedComponents"))if(component.isObject())checkObjectArray(component,"ingredients",true,errors);
        for(JsonNode step:recipe.path("steps"))if(step.isObject()&&"PROCESS".equals(step.path("type").asText())) {
            if(!step.path("bindings").isObject())errors.add(new ValidationError(child(path(step),"bindings"),"PROCESS bindings must be an object"));
            else step.path("bindings").fields().forEachRemaining(entry->{
                if(!entry.getValue().isObject())errors.add(new ValidationError(child(child(path(step),"bindings"),entry.getKey()),"binding must be an object"));
            });
        }
        if(!errors.isEmpty())throw new ValidationException(errors);
        checkPreparation(recipe,errors);
        for(JsonNode component:recipe.path("preparedComponents"))checkPreparation(component,errors);
        for(JsonNode step:recipe.path("steps"))if("TEXT".equals(step.path("type").asText()))
            checkInstructionShape(step.path("instruction"),child(path(step),"instruction"),errors);
        for(JsonNode ingredient:recipe.path("ingredients")) {
            checkQuantity(ingredient,true,errors);checkUnit(ingredient,true,errors);
            JsonNode product=ingredient.path("productTemplate");
            if(!product.isTextual()||!products.containsKey(product.asText()))
                errors.add(new ValidationError(child(path(ingredient),"productTemplate"),"Unknown ProductTemplate key/name: "+product.asText()));
        }
        for(JsonNode component:recipe.path("preparedComponents"))for(JsonNode allocation:component.path("ingredients")) {
            checkQuantity(allocation,true,errors);checkUnit(allocation,true,errors);
        }
        for(JsonNode prep:recipe.path("preparation"))scanOptionalReferences(prep.has("instruction")?prep.get("instruction"):prep,errors);
        for(JsonNode component:recipe.path("preparedComponents"))for(JsonNode prep:component.path("preparation"))
            scanOptionalReferences(prep.has("instruction")?prep.get("instruction"):prep,errors);
        for(JsonNode step:recipe.path("steps"))if("TEXT".equals(step.path("type").asText()))scanOptionalReferences(step.path("instruction"),errors);
        for(JsonNode step:recipe.path("steps"))if("PROCESS".equals(step.path("type").asText())) {
            step.path("bindings").elements().forEachRemaining(binding->{
                boolean ingredient=binding.hasNonNull("ingredient");
                checkQuantity(binding,ingredient,errors);checkUnit(binding,ingredient,errors);
            });
        }
        if(!errors.isEmpty())throw new ValidationException(errors);
    }
    private void scanOptionalReferences(JsonNode node,List<ValidationError> errors) {
        if(node.isObject()&&node.path("parts").isArray())for(JsonNode part:node.path("parts")) {
            if(part.hasNonNull("ingredient")) {
                checkQuantity(part,false,errors);checkUnit(part,false,errors);
                if(part.hasNonNull("quantity")!=part.hasNonNull("unit"))
                    errors.add(new ValidationError(child(path(part),part.hasNonNull("quantity")?"unit":"quantity"),"partial ingredient reference requires both quantity and unit"));
            }
        }

    }
    private void checkPreparation(JsonNode parent,List<ValidationError> errors) {
        JsonNode values=parent.path("preparation");String prefix=child(path(parent),"preparation");
        if(values.isMissingNode())return;
        if(!values.isArray()) {errors.add(new ValidationError(prefix,"must be an array"));return;}
        for(int i=0;i<values.size();i++) {
            JsonNode value=values.get(i);String at=prefix+"["+i+"]";
            checkInstructionShape(value.has("instruction")?value.get("instruction"):value,value.has("instruction")?child(at,"instruction"):at,errors);
        }
    }
    private void checkInstructionShape(JsonNode value,String at,List<ValidationError> errors) {
        if(value.isTextual()&&!value.asText().isBlank())return;
        if(!value.isObject()) {errors.add(new ValidationError(at,"instruction must be non-empty text or structured parts"));return;}
        if(!value.path("parts").isArray()||value.path("parts").isEmpty())
            errors.add(new ValidationError(child(at,"parts"),"structured instruction requires non-empty parts"));
    }
    private void checkObjectArray(JsonNode parent,String field,boolean required,List<ValidationError> errors) {
        JsonNode array=parent.path(field);String arrayPath=child(path(parent),field);
        if(array.isMissingNode()&&!required)return;
        if(!array.isArray()||(required&&array.isEmpty())) {
            errors.add(new ValidationError(arrayPath,required?"must be a non-empty array":"must be an array"));return;
        }
        for(int i=0;i<array.size();i++)if(!array.get(i).isObject())
            errors.add(new ValidationError(arrayPath+"["+i+"]","must be an object"));
    }
    private void checkQuantity(JsonNode node,boolean required,List<ValidationError> errors) {
        JsonNode quantity=node.path("quantity");
        if(!required&&(quantity.isMissingNode()||quantity.isNull()))return;
        if(!quantity.isNumber()||quantity.decimalValue().signum()<=0)
            errors.add(new ValidationError(child(path(node),"quantity"),"quantity must be greater than zero"));
    }
    private void checkUnit(JsonNode node,boolean required,List<ValidationError> errors) {
        if(!required&&!node.hasNonNull("unit"))return;
        try {RecipeUnit.valueOf(node.path("unit").asText());}
        catch(IllegalArgumentException e) {errors.add(new ValidationError(child(path(node),"unit"),"Unknown RecipeUnit (or missing unit): "+node.path("unit").asText()));}
    }
}
