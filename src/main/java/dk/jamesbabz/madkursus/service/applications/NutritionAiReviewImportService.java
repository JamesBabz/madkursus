package dk.jamesbabz.madkursus.service.applications;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import dk.jamesbabz.madkursus.service.exceptions.InvalidInputException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NutritionAiReviewImportService {
    @JsonIgnoreProperties(ignoreUnknown=true) public record ReviewResult(List<Decision> decisions){}
    @JsonIgnoreProperties(ignoreUnknown=true) public record Decision(String productTemplateKey,String decision,String provider,
                                                                     String foodId,String confidence,String reason){}
    public record Coverage(int completeRecipes,int totalRecipes,int coveredIngredients,int totalIngredients){}
    public record Result(int matchHighFound,int applied,int rejectedByValidation,int skippedAlreadyApproved,List<String> changedProductTemplates,
                         Coverage beforeCoverage,Coverage afterCoverage,int remainingUnresolved,List<String> validationErrors){}
    private final NutritionAdminService nutrition;private final DtuNutritionAdminService dtu;private final ObjectMapper mapper;
    public NutritionAiReviewImportService(NutritionAdminService nutrition,DtuNutritionAdminService dtu,ObjectMapper mapper){this.nutrition=nutrition;this.dtu=dtu;this.mapper=mapper;}

    @Transactional
    public Result importFile(Path path) throws Exception{
        ReviewResult input=mapper.readValue(path.toFile(),ReviewResult.class);List<Decision> selected=input.decisions()==null?List.of():input.decisions().stream()
                .filter(value->"MATCH".equals(value.decision())&&"HIGH".equals(value.confidence())).toList();
        Map<String,NutritionAdminService.Entry> templates=new LinkedHashMap<>();nutrition.list("ALL","").forEach(entry->templates.put(entry.key(),entry));
        Map<String,DtuNutritionAdminService.DatasetFood> bundled=bundledFoods();List<String> errors=new ArrayList<>();Set<String> seen=new LinkedHashSet<>();List<Validated> valid=new ArrayList<>();int skippedApproved=0;
        for(Decision decision:selected){if(decision.productTemplateKey()==null||!seen.add(decision.productTemplateKey())){errors.add("Duplicate or missing ProductTemplate key: "+decision.productTemplateKey());continue;}
            var template=templates.get(decision.productTemplateKey());if(template==null){errors.add("Unknown ProductTemplate key: "+decision.productTemplateKey());continue;}
            if(!"DTU".equals(decision.provider())){errors.add("Unsupported provider for "+decision.productTemplateKey()+": "+decision.provider());continue;}
            var bundledFood=bundled.get(decision.foodId());if(bundledFood==null){errors.add("Unknown bundled DTU food ID for "+decision.productTemplateKey()+": "+decision.foodId());continue;}
            var current=dtu.search(decision.foodId()).stream().filter(food->food.foodId().equals(decision.foodId())&&food.datasetVersion().equals("5.5")).findFirst();
            if(current.isEmpty()){errors.add("DTU food is not present in current catalog for "+decision.productTemplateKey()+": "+decision.foodId());continue;}
            if(template.template().nutritionData()!=null){skippedApproved++;continue;}
            valid.add(new Validated(decision,template,current.get()));}
        if(!errors.isEmpty())return new Result(selected.size(),0,errors.size(),skippedApproved,List.of(),coverage(),coverage(),
                (int)templates.values().stream().filter(entry->entry.template().nutritionData()==null).count(),List.copyOf(errors));
        Coverage before=coverage();List<String> changed=new ArrayList<>();for(Validated value:valid){dtu.approveReviewed(new DtuNutritionAdminService.Approval(
                value.template().template().id(),value.food().datasetVersion(),value.food().foodId()),nutrition,value.decision().reason());changed.add(value.decision().productTemplateKey());}
        return new Result(selected.size(),changed.size(),0,skippedApproved,List.copyOf(changed),before,coverage(),nutrition.list("MISSING","").size(),List.of());
    }
    private Map<String,DtuNutritionAdminService.DatasetFood> bundledFoods() throws Exception{try(InputStream input=getClass().getClassLoader().getResourceAsStream("nutrition/dtu-frida-5.5.json")){if(input==null)throw new InvalidInputException("Bundled DTU reference is missing");var data=mapper.readValue(input,DtuNutritionAdminService.Dataset.class);Map<String,DtuNutritionAdminService.DatasetFood> result=new LinkedHashMap<>();data.foods().forEach(food->result.put(food.foodId(),food));return result;}}
    private Coverage coverage(){var recipes=nutrition.coverage();return new Coverage((int)recipes.stream().filter(value->value.coveredIngredients()==value.totalIngredients()).count(),recipes.size(),recipes.stream().mapToInt(NutritionAdminService.RecipeCoverage::coveredIngredients).sum(),recipes.stream().mapToInt(NutritionAdminService.RecipeCoverage::totalIngredients).sum());}
    private record Validated(Decision decision,NutritionAdminService.Entry template,DtuNutritionAdminService.Food food){}
}
