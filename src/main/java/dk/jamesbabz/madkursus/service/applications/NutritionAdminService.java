package dk.jamesbabz.madkursus.service.applications;

import com.fasterxml.jackson.databind.ObjectMapper;
import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.ports.ProductTemplatePort;
import dk.jamesbabz.madkursus.service.ports.RecipeTemplatePort;
import java.io.InputStream;import java.util.*;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;

@Service
public class NutritionAdminService {
    public enum Status{MISSING,KNOWN_ZERO,KNOWN}
    public record Entry(ProductTemplate template,String key,Status status,int recipeTemplateUsageCount){}
    public record RecipeCoverage(UUID recipeTemplateId,String name,int coveredIngredients,int totalIngredients,List<Entry> missing){}
    private final ProductTemplatePort port;private final RecipeTemplatePort recipes;private final Map<UUID,String> keys;
    public NutritionAdminService(ProductTemplatePort port,RecipeTemplatePort recipes,ObjectMapper mapper){this.port=port;this.recipes=recipes;this.keys=loadKeys(mapper);}
    public List<Entry> list(String status,String search){Map<UUID,Integer> usage=usage();return port.search(search,null).stream().map(t->entry(t,usage.getOrDefault(t.id(),0))).filter(e->status==null||status.equals("ALL")||status.equals("KNOWN")&&e.status()!=Status.MISSING||e.status().name().equals(status)).sorted(Comparator.comparingInt(Entry::recipeTemplateUsageCount).reversed().thenComparing(e->e.template().name())).toList();}
    public List<RecipeCoverage> coverage(){Map<UUID,Integer> usage=usage();return recipes.search("").stream().map(recipe->{List<Entry> missing=recipe.ingredients().stream().map(RecipeTemplateIngredient::productTemplate).distinct().filter(t->t.nutritionData()==null).map(t->entry(t,usage.getOrDefault(t.id(),0))).toList();int total=(int)recipe.ingredients().stream().map(i->i.productTemplate().id()).distinct().count();return new RecipeCoverage(recipe.id(),recipe.name(),total-missing.size(),total,missing);}).sorted(Comparator.comparingInt((RecipeCoverage r)->r.missing().size()).reversed().thenComparing(RecipeCoverage::name)).toList();}
    @Transactional public Entry update(UUID id,NutritionData data){port.findById(id).orElseThrow(()->new dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException("Product template",id));if(data.basisUnit()!=RecipeUnit.GRAM&&data.basisUnit()!=RecipeUnit.MILLILITER&&data.basisUnit()!=RecipeUnit.PIECE)throw new dk.jamesbabz.madkursus.service.exceptions.InvalidInputException("Nutrition basis must be gram, milliliter or piece");return entry(port.updateNutrition(id,data),usage().getOrDefault(id,0));}
    private Entry entry(ProductTemplate t,int usage){Status status=t.nutritionData()==null?Status.MISSING:t.nutritionData().carbohydrateGrams().signum()==0?Status.KNOWN_ZERO:Status.KNOWN;return new Entry(t,keys.getOrDefault(t.id(),t.name().toUpperCase(Locale.ROOT)),status,usage);}
    private Map<UUID,Integer> usage(){Map<UUID,Integer> result=new HashMap<>();for(RecipeTemplate recipe:recipes.search(""))for(UUID id:recipe.ingredients().stream().map(i->i.productTemplate().id()).distinct().toList())result.merge(id,1,Integer::sum);return result;}
    private Map<UUID,String> loadKeys(ObjectMapper mapper){try(InputStream in=getClass().getClassLoader().getResourceAsStream("seed/product-templates.json")){Map<UUID,String> result=new HashMap<>();for(var node:mapper.readTree(in).path("products"))result.put(UUID.fromString(node.path("id").asText()),node.path("key").asText());return Map.copyOf(result);}catch(Exception e){throw new IllegalStateException("Cannot load ProductTemplate catalog keys",e);}}
}
