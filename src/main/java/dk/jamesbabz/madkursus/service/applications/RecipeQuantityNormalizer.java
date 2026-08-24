package dk.jamesbabz.madkursus.service.applications;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import dk.jamesbabz.madkursus.service.models.*;
import org.springframework.stereotype.Component;

@Component
public class RecipeQuantityNormalizer {
    public NormalizedRecipeQuantity normalize(BigDecimal value,RecipeUnit recipeUnit,ProductTemplate template){
        Unit storage=template.defaultUnit();RecipeUnit target=RecipeUnit.storage(storage);
        Optional<BigDecimal> converted=convert(value,recipeUnit,target,template.conversions());
        return converted.map(quantity->new NormalizedRecipeQuantity(quantity,storage,null)).orElseGet(()->unresolved(recipeUnit,storage));
    }
    public NormalizedRecipeQuantity normalize(BigDecimal value, RecipeUnit recipeUnit, Unit storage) {
        return recipeUnit.convert(value,RecipeUnit.storage(storage)).map(quantity->new NormalizedRecipeQuantity(quantity,storage,null)).orElseGet(()->unresolved(recipeUnit,storage));
    }
    public Optional<BigDecimal> convert(BigDecimal value,RecipeUnit from,RecipeUnit to,List<ProductTemplateUnitConversion> specific){
        record Node(RecipeUnit unit,BigDecimal factor){} java.util.ArrayDeque<Node> queue=new java.util.ArrayDeque<>();java.util.EnumSet<RecipeUnit> visited=java.util.EnumSet.noneOf(RecipeUnit.class);queue.add(new Node(from,BigDecimal.ONE));visited.add(from);
        while(!queue.isEmpty()){Node current=queue.remove();if(current.unit()==to)return Optional.of(value.multiply(current.factor()).setScale(12,java.math.RoundingMode.HALF_UP).stripTrailingZeros());for(RecipeUnit next:RecipeUnit.values()){Optional<BigDecimal> one=current.unit().convert(BigDecimal.ONE,next);if(one.isPresent()&&visited.add(next))queue.add(new Node(next,current.factor().multiply(one.get())));}for(ProductTemplateUnitConversion conversion:specific){RecipeUnit next=null;BigDecimal factor=null;if(conversion.fromUnit()==current.unit()){next=conversion.toUnit();factor=conversion.factor();}else if(conversion.toUnit()==current.unit()){next=conversion.fromUnit();factor=BigDecimal.ONE.divide(conversion.factor(),java.math.MathContext.DECIMAL128);}if(next!=null&&visited.add(next))queue.add(new Node(next,current.factor().multiply(factor)));}}
        return Optional.empty();
    }
    private NormalizedRecipeQuantity unresolved(RecipeUnit recipeUnit,Unit storage){return new NormalizedRecipeQuantity(null,storage,"Opskriften bruger "+recipeUnit.name()+", men lageret føres i "+storage.name()+". Kan ikke beregnes automatisk.");}
}
