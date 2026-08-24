package dk.jamesbabz.madkursus.service.applications;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import dk.jamesbabz.madkursus.service.exceptions.*;
import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.ports.CookingProcessPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class CookingProcessService {
    private static final Pattern PLACEHOLDER=Pattern.compile("\\{([A-Z][A-Z0-9_]*)}");
    private final CookingProcessPort port;
    private final KitchenEquipmentService equipment;

    public List<CookingProcess> search(String query) {
        return port.search(query==null?"":query.trim()).stream().peek(this::validateDefinition).toList();
    }
    public CookingProcess get(UUID id) {
        CookingProcess process=port.findById(id).orElseThrow(()->new ResourceNotFoundException("Cooking process",id));
        validateDefinition(process); return process;
    }

    public void validateDefinition(CookingProcess process) {
        if(process.key()==null||process.key().isBlank()||process.name()==null||process.name().isBlank())
            throw new InvalidInputException("Cooking process key and name are required");
        Set<String> keys=new HashSet<>();
        for(CookingProcessParameter parameter:process.parameters()) {
            if(parameter.key()==null||!parameter.key().matches("[A-Z][A-Z0-9_]*")||!keys.add(parameter.key()))
                throw new InvalidInputException("Cooking process parameter keys must be valid and unique");
            validateDefault(parameter);
        }
        validateOrders(process.parameters().stream().map(CookingProcessParameter::sortOrder).toList());
        validateOrders(process.steps().stream().map(CookingProcessStep::sortOrder).toList());
        if(process.steps().isEmpty()) throw new InvalidInputException("Cooking process must contain steps");
        process.steps().forEach(step->validateTemplate(step.instructionTemplate(),keys));
        validateTemplate(process.completionCriteriaTemplate(),keys);
        validateDurationReference(process.activeDurationParameterKey(),process);
        validateDurationReference(process.passiveDurationParameterKey(),process);
        for(CookingProcessPreparationRequirement requirement:process.preparationRequirements()) {
            if(!keys.contains(requirement.parameterKey()))throw new InvalidInputException("Unknown preparation parameter: "+requirement.parameterKey());
            validateTemplate(requirement.instructionTemplate(),keys);
        }
    }

    public void validateBindings(CookingProcess process,List<CookingProcessBinding> bindings) {
        Map<String,CookingProcessBinding> supplied=new HashMap<>();
        for(CookingProcessBinding binding:bindings) {
            if(binding.parameterKey()==null||supplied.put(binding.parameterKey(),binding)!=null)
                throw new InvalidInputException("Process parameter bindings must be unique");
        }
        Set<String> declared=process.parameters().stream().map(CookingProcessParameter::key).collect(java.util.stream.Collectors.toSet());
        if(supplied.keySet().stream().anyMatch(key->!declared.contains(key)&&!isSetMember(process,key))) throw new InvalidInputException("Unknown cooking process parameter binding");
        for(CookingProcessParameter parameter:process.parameters()) {
            CookingProcessBinding binding=supplied.get(parameter.key());
            if(binding==null && parameter.normalInput() && parameter.required() && parameter.type()!=CookingProcessParameterType.INGREDIENT_LIST)
                throw new InvalidInputException("Missing required process parameter: "+parameter.label());
            if(binding!=null) validateValue(parameter,binding);
        }
        for(CookingProcessBinding binding:bindings) if(!declared.contains(binding.parameterKey())) validateValue(setParameter(process,binding.parameterKey()),binding);
    }

    public RenderedCookingProcess render(UUID processId,List<CookingProcessBinding> bindings) {
        CookingProcess process=get(processId); validateBindings(process,bindings);
        Map<String,CookingProcessBinding> supplied=bindings.stream().collect(java.util.stream.Collectors.toMap(CookingProcessBinding::parameterKey,b->b));
        Map<String,String> values=new HashMap<>();
        for(CookingProcessParameter parameter:process.parameters()) {
            CookingProcessBinding binding=supplied.get(parameter.key());
            CookingProcessValue derived=binding==null&&parameter.source()==CookingProcessParameterSource.DERIVED?derive(parameter,supplied):null;
            values.put(parameter.key(),parameter.type()==CookingProcessParameterType.INGREDIENT_LIST?ingredientList(bindings,parameter.key()):format(parameter,binding,derived));
        }
        List<String> warnings=equipmentWarnings(process);
        List<String> instructions=process.steps().stream().sorted(Comparator.comparingInt(CookingProcessStep::sortOrder))
                .map(step->substitute(step.instructionTemplate(),values)).toList();
        Integer active=resolvedDuration(process.activeDurationSeconds(),process.activeDurationParameterKey(),process,supplied);
        Integer passive=resolvedDuration(process.passiveDurationSeconds(),process.passiveDurationParameterKey(),process,supplied);
        List<String> preparation=renderPreparation(process,bindings,values);
        return new RenderedCookingProcess(instructions,substitute(process.completionCriteriaTemplate(),values),warnings,
                process.name(),active,passive,durationSummary(active,passive),preparation,inputSummary(bindings));
    }

    private void validateDurationReference(String key,CookingProcess process){if(key==null)return;CookingProcessParameter parameter=process.parameters().stream().filter(p->p.key().equals(key)).findFirst().orElseThrow(()->new InvalidInputException("Unknown duration parameter: "+key));if(parameter.type()!=CookingProcessParameterType.DURATION)throw new InvalidInputException("Timing reference must be a duration: "+key);}
    private Integer resolvedDuration(Integer fixed,String key,CookingProcess process,Map<String,CookingProcessBinding> supplied){if(key==null)return fixed;CookingProcessBinding binding=supplied.get(key);if(binding!=null&&binding.value()!=null&&binding.value().durationSeconds()!=null)return binding.value().durationSeconds();return process.parameters().stream().filter(p->p.key().equals(key)).map(CookingProcessParameter::defaultValue).filter(Objects::nonNull).map(CookingProcessValue::durationSeconds).filter(Objects::nonNull).findFirst().orElse(fixed);}
    public String durationSummary(Integer active,Integer passive){List<String> parts=new ArrayList<>();if(active!=null&&active>0)parts.add(shortDuration(active)+" aktiv");if(passive!=null&&passive>0)parts.add(shortDuration(passive)+" ventetid");return String.join(" · ",parts);}
    private String shortDuration(int seconds){int minutes=seconds/60,remainder=seconds%60;if(minutes==0)return remainder+" sek";return remainder==0?minutes+" min":minutes+" min "+remainder+" sek";}
    private List<String> renderPreparation(CookingProcess process,List<CookingProcessBinding> bindings,Map<String,String> values){LinkedHashMap<String,String> result=new LinkedHashMap<>();for(CookingProcessPreparationRequirement requirement:process.preparationRequirements().stream().sorted(Comparator.comparingInt(CookingProcessPreparationRequirement::sortOrder)).toList()){CookingProcessParameter parameter=process.parameters().stream().filter(p->p.key().equals(requirement.parameterKey())).findFirst().orElseThrow();if(parameter.type()==CookingProcessParameterType.INGREDIENT_LIST){for(CookingProcessBinding member:bindings.stream().filter(b->b.parameterKey().startsWith(parameter.key()+":")).toList()){String concrete=format(setParameter(process,member.parameterKey()),member,null);putPreparation(result,substitute(requirement.instructionTemplate(),Map.of(parameter.key(),concrete)));}}else{String concrete=values.getOrDefault(parameter.key(),"");if(!concrete.isBlank())putPreparation(result,substitute(requirement.instructionTemplate(),Map.of(parameter.key(),concrete)));}}return List.copyOf(result.values());}
    private void putPreparation(Map<String,String> result,String instruction){String key=instruction.toLowerCase(Locale.forLanguageTag("da")).replaceAll("[^a-zæøå0-9]+"," ").trim();result.putIfAbsent(key,instruction);}

    private List<String> equipmentWarnings(CookingProcess process) {
        Set<EquipmentType> available=equipment.getAll().stream().filter(KitchenEquipment::active)
                .map(KitchenEquipment::equipmentType).collect(java.util.stream.Collectors.toSet());
        List<String> warnings=new ArrayList<>();
        for(CookingProcessEquipmentRequirement requirement:process.equipmentRequirements()) if(!available.contains(requirement.equipmentType()))
            warnings.add((requirement.level()==EquipmentRequirementLevel.REQUIRED?"Mangler påkrævet udstyr: ":"Anbefalet udstyr mangler: ")+equipmentName(requirement.equipmentType()));
        return warnings;
    }
    private String format(CookingProcessParameter p,CookingProcessBinding binding,CookingProcessValue derived) {
        if(binding!=null&&binding.preparedComponent()!=null)return componentContents(binding.preparedComponent());
        CookingProcessValue value=binding==null?(derived==null?p.defaultValue():derived):binding.value();
        if(value==null) return "";
        return switch(p.type()) {
            case INGREDIENT_QUANTITY -> binding==null||binding.productTemplate()==null?quantity(value.quantity(),value.unit()):binding.productTemplate().name()+" ("+quantity(value.quantity(),value.unit())+")";
            case INGREDIENT_LIST -> "";
            case QUANTITY -> quantity(value.quantity(),value.unit()!=null?value.unit():p.unit());
            case DURATION -> duration(value.durationSeconds());
            case TEMPERATURE -> value.temperatureCelsius()==null?"":value.temperatureCelsius()+" °C";
            case HEAT_LEVEL -> heat(value.heatLevel());
            case NUMBER -> decimal(value.number());
            case TEXT -> Objects.toString(value.text(),"");
        };
    }
    private String heat(HeatLevel level) {
        if(level==null)return "";
        Optional<KitchenEquipment> stove=equipment.findPreferredStove();
        if(stove.isPresent()) try {
            String setting=equipment.resolveHeatSetting(stove.get(),level);
            return setting.matches("-?\\d+")?"trin "+setting:setting.toLowerCase(Locale.forLanguageTag("da"));
        } catch(InvalidInputException ignored) {}
        return switch(level){case LOW->"lav varme";case MEDIUM_LOW->"middellav varme";case MEDIUM->"middel varme";case MEDIUM_HIGH->"middelhøj varme";case HIGH->"høj varme";case MAX->"maksimal varme";};
    }
    private String duration(Integer seconds){if(seconds==null)return "";int minutes=seconds/60,remainder=seconds%60;if(minutes==0)return remainder+" sekunder";String minuteText=minutes+" "+(minutes==1?"minut":"minutter");return remainder==0?minuteText:minuteText+" og "+remainder+" sekunder";}
    private String quantity(BigDecimal value,RecipeUnit unit){if(value==null)return "";if(unit==RecipeUnit.GRINDER_TURN)value=value.setScale(0,java.math.RoundingMode.HALF_UP);if(unit==RecipeUnit.MILLILITER&&value.compareTo(BigDecimal.valueOf(1000))>=0&&value.remainder(BigDecimal.valueOf(1000)).signum()==0){BigDecimal liters=value.divide(BigDecimal.valueOf(1000));return decimal(liters)+" "+(liters.compareTo(BigDecimal.ONE)==0?"liter":"liter");}return decimal(value)+" "+unitName(unit,value);}

    private CookingProcessValue derive(CookingProcessParameter parameter,Map<String,CookingProcessBinding> supplied){
        CookingProcessBinding source=supplied.get(parameter.derivedFrom());
        if(source==null||source.value()==null||source.value().quantity()==null)return null;
        BigDecimal grams=source.value().quantity();
        BigDecimal amount=switch(parameter.derivedRule()){
            case POTATO_WATER_PER_GRAM->grams.multiply(new BigDecimal("3"));
            case POTATO_SALT_PER_GRAM->grams.divide(new BigDecimal("1000"),3,java.math.RoundingMode.HALF_UP);
            case PASTA_WATER_PER_GRAM->grams.multiply(new BigDecimal("10"));
            case PASTA_SALT_PER_GRAM->grams.divide(new BigDecimal("100"),3,java.math.RoundingMode.HALF_UP);
            case RICE_WATER_PER_GRAM->grams.multiply(new BigDecimal("1.5"));
        };
        RecipeUnit unit=switch(parameter.derivedRule()){case POTATO_SALT_PER_GRAM,PASTA_SALT_PER_GRAM->RecipeUnit.TEASPOON;default->RecipeUnit.MILLILITER;};
        return new CookingProcessValue(amount.stripTrailingZeros(),unit,null,null,null,null,null);
    }
    private boolean isSetMember(CookingProcess process,String key){int colon=key.indexOf(':');if(colon<1)return false;return process.parameters().stream().anyMatch(p->p.key().equals(key.substring(0,colon))&&p.type()==CookingProcessParameterType.INGREDIENT_LIST);}
    private CookingProcessParameter setParameter(CookingProcess process,String key){String root=key.substring(0,key.indexOf(':'));CookingProcessParameter p=process.parameters().stream().filter(v->v.key().equals(root)).findFirst().orElseThrow();return new CookingProcessParameter(p.id(),key,p.label(),CookingProcessParameterType.INGREDIENT_QUANTITY,false,null,null,p.sortOrder(),CookingProcessParameterSource.INPUT,null,null);}
    private String ingredientList(List<CookingProcessBinding> bindings,String key){List<CookingProcessBinding> members=bindings.stream().filter(value->value.parameterKey().startsWith(key+":")).toList();if(members.isEmpty())members=bindings;List<String> names=members.stream().filter(value->value.productTemplate()!=null&&value.value()!=null&&value.value().quantity()!=null).map(value->value.productTemplate().name().toLowerCase(Locale.forLanguageTag("da"))).distinct().toList();if(names.isEmpty())return "de øvrige ingredienser";if(names.size()==1)return names.getFirst();return String.join(", ",names.subList(0,names.size()-1))+" og "+names.getLast();}
    private String decimal(BigDecimal value){if(value==null)return "";BigDecimal whole=value.setScale(0,java.math.RoundingMode.DOWN),fraction=value.subtract(whole);String glyph=fraction.compareTo(new BigDecimal("0.25"))==0?"¼":fraction.compareTo(new BigDecimal("0.5"))==0?"½":fraction.compareTo(new BigDecimal("0.75"))==0?"¾":null;if(glyph!=null)return whole.signum()==0?glyph:whole.toPlainString()+glyph;return NumberFormat.getNumberInstance(Locale.forLanguageTag("da-DK")).format(value.stripTrailingZeros());}
    private String unitName(RecipeUnit unit,BigDecimal value){if(unit==null)return "";return switch(unit){case GRAM->"g";case MILLILITER->"ml";case PIECE->"stk";case TEASPOON->"tsk";case TABLESPOON->"spsk";case DECILITER->"dl";case GRINDER_TURN->value.compareTo(BigDecimal.ONE)==0?"omgang":"omgange";};}
    private String equipmentName(EquipmentType type){return switch(type){case STOVE->"komfur";case OVEN->"ovn";case POT->"gryde";case PAN->"stegepande";case AIR_FRYER->"airfryer";case THERMOMETER->"stegetermometer";case MICROWAVE->"mikroovn";};}
    private String substitute(String template,Map<String,String> values){if(template==null)return null;Matcher m=PLACEHOLDER.matcher(template);StringBuffer out=new StringBuffer();while(m.find())m.appendReplacement(out,Matcher.quoteReplacement(values.getOrDefault(m.group(1),"")));m.appendTail(out);return out.toString().replace(":.",".").replaceAll("\\s+([.,])","$1").replaceAll(" {2,}"," ").trim();}
    private void validateTemplate(String template,Set<String> keys){if(template==null||template.isBlank())throw new InvalidInputException("Process instruction and completion criterion are required");Matcher m=PLACEHOLDER.matcher(template);while(m.find())if(!keys.contains(m.group(1)))throw new InvalidInputException("Unknown process placeholder: "+m.group(1));String stripped=m.replaceAll("");if(stripped.contains("{")||stripped.contains("}"))throw new InvalidInputException("Invalid process placeholder syntax");}
    private void validateDefault(CookingProcessParameter p){if(p.required()&&!hasValue(p.defaultValue(),p.type())&&p.type()!=CookingProcessParameterType.INGREDIENT_QUANTITY)return; if(p.defaultValue()!=null&&hasAny(p.defaultValue()))validateValue(p,new CookingProcessBinding(null,p.key(),null,p.defaultValue()));}
    private void validateValue(CookingProcessParameter p,CookingProcessBinding b){CookingProcessBindingSemantics.validate(p.type(),p.label(),b.recipeIngredientId()!=null,b.productTemplate()!=null,b.preparedComponent()!=null,b.value(),p.unit());}
    private String componentContents(PreparedComponent component){List<String> values=component.ingredients().stream().sorted(Comparator.comparingInt(PreparedComponentIngredient::sortOrder)).map(value->value.productTemplate().name()+" ("+quantity(value.quantity(),value.unit())+")").toList();if(values.isEmpty())return component.name();if(values.size()==1)return values.getFirst();return String.join(", ",values.subList(0,values.size()-1))+" og "+values.getLast();}
    private String inputSummary(List<CookingProcessBinding> bindings){List<String> values=bindings.stream().filter(b->b.preparedComponent()!=null||b.productTemplate()!=null).map(b->b.preparedComponent()!=null?b.preparedComponent().name():(b.value()!=null&&b.value().quantity()!=null?quantity(b.value().quantity(),b.value().unit())+" "+b.productTemplate().name():b.productTemplate().name())).distinct().toList();if(values.isEmpty())return "";List<String> shown=values.subList(0,Math.min(2,values.size()));return String.join(" · ",shown)+(values.size()>2?" · "+(values.size()-2)+" øvrige":"");}
    private boolean hasValue(CookingProcessValue v,CookingProcessParameterType type){if(type==CookingProcessParameterType.INGREDIENT_LIST)return true;if(v==null)return false;return switch(type){case INGREDIENT_QUANTITY,QUANTITY->v.quantity()!=null;case INGREDIENT_LIST->true;case DURATION->v.durationSeconds()!=null;case TEMPERATURE->v.temperatureCelsius()!=null;case HEAT_LEVEL->v.heatLevel()!=null;case NUMBER->v.number()!=null;case TEXT->v.text()!=null&&!v.text().isBlank();};}

    public List<String> equipmentOverview(Collection<UUID> processIds,List<RecipeEquipmentRequirement> explicit){LinkedHashMap<String,String> result=new LinkedHashMap<>();for(UUID processId:processIds)for(CookingProcessEquipmentRequirement requirement:get(processId).equipmentRequirements())if(requirement.level()==EquipmentRequirementLevel.REQUIRED)result.putIfAbsent("type:"+requirement.equipmentType(),equipmentDisplay(requirement.equipmentType()));for(RecipeEquipmentRequirement requirement:explicit){if(requirement.equipmentType()!=null)result.putIfAbsent("type:"+requirement.equipmentType(),equipmentDisplay(requirement.equipmentType()));else if(requirement.label()!=null&&!requirement.label().isBlank())result.putIfAbsent("label:"+requirement.label().trim().toLowerCase(Locale.ROOT),requirement.label().trim());}return List.copyOf(result.values());}
    private String equipmentDisplay(EquipmentType type){List<KitchenEquipment> matches=equipment.getAll().stream().filter(KitchenEquipment::active).filter(value->value.equipmentType()==type).toList();return matches.size()==1?matches.getFirst().name():switch(type){case STOVE->"Komfur";case OVEN->"Ovn";case POT->"Gryde";case PAN->"Stegepande";case AIR_FRYER->"Airfryer";case THERMOMETER->"Stegetermometer";case MICROWAVE->"Mikroovn";};}
    private boolean hasAny(CookingProcessValue v){return v.quantity()!=null||v.durationSeconds()!=null||v.temperatureCelsius()!=null||v.heatLevel()!=null||v.number()!=null||v.text()!=null;}
    private void validateOrders(List<Integer> values){if(values.stream().anyMatch(v->v==null||v<=0)||new HashSet<>(values).size()!=values.size())throw new InvalidInputException("Sort order must be positive and unique");}
}
