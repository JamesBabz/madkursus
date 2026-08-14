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
    }

    public void validateBindings(CookingProcess process,List<CookingProcessBinding> bindings) {
        Map<String,CookingProcessBinding> supplied=new HashMap<>();
        for(CookingProcessBinding binding:bindings) {
            if(binding.parameterKey()==null||supplied.put(binding.parameterKey(),binding)!=null)
                throw new InvalidInputException("Process parameter bindings must be unique");
        }
        Set<String> declared=process.parameters().stream().map(CookingProcessParameter::key).collect(java.util.stream.Collectors.toSet());
        if(!declared.containsAll(supplied.keySet())) throw new InvalidInputException("Unknown cooking process parameter binding");
        for(CookingProcessParameter parameter:process.parameters()) {
            CookingProcessBinding binding=supplied.get(parameter.key());
            if(binding==null && parameter.required() && !hasValue(parameter.defaultValue(),parameter.type()))
                throw new InvalidInputException("Missing required process parameter: "+parameter.label());
            if(binding!=null) validateValue(parameter,binding);
        }
    }

    public RenderedCookingProcess render(UUID processId,List<CookingProcessBinding> bindings) {
        CookingProcess process=get(processId); validateBindings(process,bindings);
        Map<String,CookingProcessBinding> supplied=bindings.stream().collect(java.util.stream.Collectors.toMap(CookingProcessBinding::parameterKey,b->b));
        Map<String,String> values=new HashMap<>();
        for(CookingProcessParameter parameter:process.parameters())
            values.put(parameter.key(),format(parameter,supplied.get(parameter.key())));
        List<String> warnings=equipmentWarnings(process);
        List<String> instructions=process.steps().stream().sorted(Comparator.comparingInt(CookingProcessStep::sortOrder))
                .map(step->substitute(step.instructionTemplate(),values)).toList();
        return new RenderedCookingProcess(instructions,substitute(process.completionCriteriaTemplate(),values),warnings);
    }

    private List<String> equipmentWarnings(CookingProcess process) {
        Set<EquipmentType> available=equipment.getAll().stream().filter(KitchenEquipment::active)
                .map(KitchenEquipment::equipmentType).collect(java.util.stream.Collectors.toSet());
        List<String> warnings=new ArrayList<>();
        for(CookingProcessEquipmentRequirement requirement:process.equipmentRequirements()) if(!available.contains(requirement.equipmentType()))
            warnings.add((requirement.level()==EquipmentRequirementLevel.REQUIRED?"Mangler påkrævet udstyr: ":"Anbefalet udstyr mangler: ")+equipmentName(requirement.equipmentType()));
        return warnings;
    }
    private String format(CookingProcessParameter p,CookingProcessBinding binding) {
        CookingProcessValue value=binding==null?p.defaultValue():binding.value();
        if(value==null) return "";
        return switch(p.type()) {
            case INGREDIENT_QUANTITY -> binding==null||binding.productTemplate()==null?"":binding.productTemplate().name()+" ("+quantity(value.quantity(),value.unit())+")";
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
    private String quantity(BigDecimal value,RecipeUnit unit){if(value==null)return "";return decimal(value)+" "+unitName(unit);}
    private String decimal(BigDecimal value){if(value==null)return "";return NumberFormat.getNumberInstance(Locale.forLanguageTag("da-DK")).format(value.stripTrailingZeros());}
    private String unitName(RecipeUnit unit){if(unit==null)return "";return switch(unit){case GRAM->"g";case MILLILITER->"ml";case PIECE->"stk";case TEASPOON->"tsk";case TABLESPOON->"spsk";case DECILITER->"dl";};}
    private String equipmentName(EquipmentType type){return switch(type){case STOVE->"komfur";case OVEN->"ovn";case POT->"gryde";case PAN->"stegepande";case AIR_FRYER->"airfryer";case THERMOMETER->"stegetermometer";case MICROWAVE->"mikroovn";};}
    private String substitute(String template,Map<String,String> values){if(template==null)return null;Matcher m=PLACEHOLDER.matcher(template);StringBuffer out=new StringBuffer();while(m.find())m.appendReplacement(out,Matcher.quoteReplacement(values.getOrDefault(m.group(1),"")));m.appendTail(out);return out.toString().replaceAll("\\s+([.,])","$1").replaceAll(" {2,}"," ").trim();}
    private void validateTemplate(String template,Set<String> keys){if(template==null||template.isBlank())throw new InvalidInputException("Process instruction and completion criterion are required");Matcher m=PLACEHOLDER.matcher(template);while(m.find())if(!keys.contains(m.group(1)))throw new InvalidInputException("Unknown process placeholder: "+m.group(1));String stripped=m.replaceAll("");if(stripped.contains("{")||stripped.contains("}"))throw new InvalidInputException("Invalid process placeholder syntax");}
    private void validateDefault(CookingProcessParameter p){if(p.required()&&!hasValue(p.defaultValue(),p.type())&&p.type()!=CookingProcessParameterType.INGREDIENT_QUANTITY)return; if(p.defaultValue()!=null&&hasAny(p.defaultValue()))validateValue(p,new CookingProcessBinding(null,p.key(),null,p.defaultValue()));}
    private void validateValue(CookingProcessParameter p,CookingProcessBinding b){CookingProcessValue v=b.value();if(v==null)throw new InvalidInputException("Parameter value is required: "+p.label());boolean valid=switch(p.type()){case INGREDIENT_QUANTITY->b.recipeIngredientId()!=null&&b.productTemplate()!=null&&v.quantity()!=null&&v.quantity().signum()>0&&v.unit()!=null;case QUANTITY->v.quantity()!=null&&v.quantity().signum()>0&&(v.unit()!=null||p.unit()!=null);case DURATION->v.durationSeconds()!=null&&v.durationSeconds()>0;case TEMPERATURE->v.temperatureCelsius()!=null;case HEAT_LEVEL->v.heatLevel()!=null;case NUMBER->v.number()!=null;case TEXT->v.text()!=null&&!v.text().isBlank();};if(!valid)throw new InvalidInputException("Invalid value for process parameter: "+p.label());}
    private boolean hasValue(CookingProcessValue v,CookingProcessParameterType type){if(v==null)return false;return switch(type){case INGREDIENT_QUANTITY,QUANTITY->v.quantity()!=null;case DURATION->v.durationSeconds()!=null;case TEMPERATURE->v.temperatureCelsius()!=null;case HEAT_LEVEL->v.heatLevel()!=null;case NUMBER->v.number()!=null;case TEXT->v.text()!=null&&!v.text().isBlank();};}
    private boolean hasAny(CookingProcessValue v){return v.quantity()!=null||v.durationSeconds()!=null||v.temperatureCelsius()!=null||v.heatLevel()!=null||v.number()!=null||v.text()!=null;}
    private void validateOrders(List<Integer> values){if(values.stream().anyMatch(v->v==null||v<=0)||new HashSet<>(values).size()!=values.size())throw new InvalidInputException("Sort order must be positive and unique");}
}
