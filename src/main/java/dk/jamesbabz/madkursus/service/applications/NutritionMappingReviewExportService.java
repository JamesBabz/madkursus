package dk.jamesbabz.madkursus.service.applications;

import com.fasterxml.jackson.databind.ObjectMapper;
import dk.jamesbabz.madkursus.service.models.ProductTemplate;
import dk.jamesbabz.madkursus.service.ports.RecipeTemplatePort;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class NutritionMappingReviewExportService {
    public record Metadata(Instant generatedAt,String dtuDataset,String purpose,String reviewGoal,int productTemplateCount){}
    public record Product(String key,String name,List<String> aliases,String defaultUnit,String trackingMode,
                          int recipeUsageCount,List<String> usedByRecipes){}
    public record State(String mappingClassification,String resolution,String diagnosticReason){}
    public record Candidate(String provider,String foodId,String name,BigDecimal carbohydratePer100,String basisUnit,
                            int score,String classification,List<String> semanticFlags,String state,String reason){}
    public record Item(Product productTemplate,State currentState,List<Candidate> automaticCandidates,
                       List<Candidate> broadSearchCandidates,
                       DtuNutritionAdminService.CompatibleCandidateSummary compatibleCandidateSummary){}
    public record ReviewFile(Metadata metadata,List<Item> items){}
    public record Statistics(int unresolved,int usedByRecipes,int withAutomaticCandidates,
                             int withBroadSearchCandidatesOnly,int withoutUsefulCandidate,
                             int zeroCandidates,int oneCandidate,int twoToThreeCandidates,int fourToTenCandidates){}
    public record Result(Path json,Path markdown,Statistics statistics){}

    private final NutritionAdminService nutrition;private final DtuNutritionAdminService dtu;
    private final RecipeTemplatePort recipes;private final ObjectMapper mapper;
    public NutritionMappingReviewExportService(NutritionAdminService nutrition,DtuNutritionAdminService dtu,
                                                RecipeTemplatePort recipes,ObjectMapper mapper){this.nutrition=nutrition;this.dtu=dtu;this.recipes=recipes;this.mapper=mapper;}

    public ReviewFile generate(Instant generatedAt){
        Map<java.util.UUID,List<String>> recipeNames=new HashMap<>();
        recipes.search("").stream().filter(recipe->recipe.active()).forEach(recipe->recipe.ingredients().stream()
                .map(ingredient->ingredient.productTemplate().id()).distinct().forEach(id->recipeNames.computeIfAbsent(id,ignored->new ArrayList<>()).add(recipe.name())));
        List<Item> items=nutrition.list("ALL","").stream().filter(entry->entry.template().nutritionData()==null).map(entry->{
            ProductTemplate template=entry.template();var review=dtu.reviewCandidates(template);
            List<String> used=recipeNames.getOrDefault(template.id(),List.of()).stream().sorted().toList();
            return new Item(new Product(entry.key(),template.name(),template.aliases(),template.defaultUnit().name(),template.defaultTrackingMode().name(),used.size(),used),
                    new State(review.suggestion().classification().name(),review.suggestion().resolution().name(),review.suggestion().reason()),
                    review.automaticCandidates().stream().map(this::candidate).toList(),review.broadSearchCandidates().stream().map(this::candidate).toList(),review.compatibleCandidateSummary());
        }).sorted(Comparator.comparingInt((Item item)->item.productTemplate().recipeUsageCount()).reversed()
                .thenComparingInt(item->confidenceOrder(item.currentState().mappingClassification()))
                .thenComparing(item->item.productTemplate().name())).toList();
        validate(items);
        return new ReviewFile(new Metadata(generatedAt,"Frida "+dtu.latestVersion(),"carbohydrate-only ProductTemplate mapping review",
                "Choose a representative food entry suitable for carbohydrate grams, not for protein/fat/calorie accuracy.",items.size()),items);
    }

    public Result export(Path directory,Instant generatedAt) throws IOException{
        ReviewFile review=generate(generatedAt);Files.createDirectories(directory);Path json=directory.resolve("product-template-nutrition-review.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(json.toFile(),review);Path markdown=directory.resolve("product-template-nutrition-review.md");
        Statistics statistics=statistics(review);Files.writeString(markdown,markdown(review,statistics));return new Result(json,markdown,statistics);
    }

    public Statistics statistics(ReviewFile review){int used=0,automatic=0,broadOnly=0,none=0,zero=0,one=0,twoThree=0,fourTen=0;
        for(Item item:review.items()){if(item.productTemplate().recipeUsageCount()>0)used++;int count=item.automaticCandidates().size()+item.broadSearchCandidates().size();
            if(!item.automaticCandidates().isEmpty())automatic++;else if(!item.broadSearchCandidates().isEmpty())broadOnly++;else none++;
            if(count==0)zero++;else if(count==1)one++;else if(count<=3)twoThree++;else fourTen++;}
        return new Statistics(review.items().size(),used,automatic,broadOnly,none,zero,one,twoThree,fourTen);}

    private Candidate candidate(DtuNutritionAdminService.Candidate value){var food=value.food();List<String> flags=new ArrayList<>();
        if(food.stateDescription()!=null&&!food.stateDescription().isBlank())flags.add(food.stateDescription());
        return new Candidate("DTU",food.foodId(),food.danishName(),food.carbohydrateGrams(),"GRAM",value.score(),value.classification().name(),List.copyOf(flags),food.stateDescription(),value.reason());}
    private void validate(List<Item> items){Set<String> keys=new LinkedHashSet<>();for(Item item:items){if(!keys.add(item.productTemplate().key()))throw new IllegalStateException("Duplicate ProductTemplate key: "+item.productTemplate().key());
        if(item.productTemplate().recipeUsageCount()!=item.productTemplate().usedByRecipes().size())throw new IllegalStateException("Recipe usage mismatch: "+item.productTemplate().key());
        java.util.stream.Stream.concat(item.automaticCandidates().stream(),item.broadSearchCandidates().stream()).forEach(candidate->{if(candidate.carbohydratePer100()==null||!candidate.basisUnit().equals("GRAM")||dtu.search(candidate.foodId()).stream().noneMatch(food->food.foodId().equals(candidate.foodId())))throw new IllegalStateException("Candidate is not in DTU catalog: "+candidate.foodId());});}}
    private static int confidenceOrder(String value){return switch(value){case "EXACT"->0;case "HIGH_CONFIDENCE"->1;case "REVIEW_REQUIRED"->2;default->3;};}
    private static String markdown(ReviewFile review,Statistics stats){StringBuilder value=new StringBuilder("# Nutrition AI review export\n\n");value.append("Unresolved ProductTemplates: ").append(stats.unresolved()).append("\n\nUsed by recipes: ").append(stats.usedByRecipes()).append("\n\n| ProductTemplate | Recipes | Classification | Automatic | Broad |\n|---|---:|---|---:|---:|\n");for(Item item:review.items())value.append('|').append(item.productTemplate().name()).append('|').append(item.productTemplate().recipeUsageCount()).append('|').append(item.currentState().mappingClassification()).append('|').append(item.automaticCandidates().size()).append('|').append(item.broadSearchCandidates().size()).append("|\n");return value.toString();}
}
