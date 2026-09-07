package dk.jamesbabz.madkursus.service.applications;

import dk.jamesbabz.madkursus.service.models.*;
import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CarbohydrateCalculator {
    private CarbohydrateResult calculate(List<Ingredient> ingredients, BigDecimal portions) {
        BigDecimal total=BigDecimal.ZERO;int unknown=0;List<CarbohydrateIngredientResult> rows=new ArrayList<>();
        for(Ingredient ingredient:ingredients){ProductTemplate product=ingredient.productTemplate();Optional<BigDecimal> contribution=contribution(product,ingredient.quantity().multiply(portions),ingredient.unit());
            if(contribution.isPresent()){BigDecimal grams=contribution.get();total=total.add(grams);rows.add(new CarbohydrateIngredientResult(ingredient.id(),product.id(),product.name(),grams,true));}
            else {unknown++;rows.add(new CarbohydrateIngredientResult(ingredient.id(),product.id(),product.name(),null,false));}}
        return new CarbohydrateResult(total.divide(portions,MathContext.DECIMAL128),total,unknown==0,unknown,List.copyOf(rows));
    }
    public CarbohydrateResult calculateRecipe(List<RecipeIngredient> ingredients,BigDecimal portions){return calculate(ingredients.stream().map(Ingredient::new).toList(),portions);}
    public CarbohydrateResult calculateTemplate(List<RecipeTemplateIngredient> ingredients,BigDecimal portions){return calculate(ingredients.stream().map(Ingredient::new).toList(),portions);}
    Optional<BigDecimal> contribution(ProductTemplate product,BigDecimal quantity,RecipeUnit unit){NutritionData data=product.nutritionData();if(data==null)return Optional.empty();return convert(product,quantity,unit,data.basisUnit()).map(basisAmount->basisAmount.multiply(data.carbohydrateGrams()).divide(data.basisQuantity(),MathContext.DECIMAL128));}
    private Optional<BigDecimal> convert(ProductTemplate product,BigDecimal quantity,RecipeUnit from,RecipeUnit to){Optional<BigDecimal> generic=from.convert(quantity,to);if(generic.isPresent())return generic;for(ProductTemplateUnitConversion c:product.conversions()){if(c.fromUnit()==from&&c.toUnit()==to)return Optional.of(quantity.multiply(c.factor()));if(c.toUnit()==from&&c.fromUnit()==to)return Optional.of(quantity.divide(c.factor(),MathContext.DECIMAL128));}return Optional.empty();}
    record Ingredient(UUID id,ProductTemplate productTemplate,BigDecimal quantity,RecipeUnit unit){Ingredient(RecipeIngredient i){this(i.id(),i.productTemplate(),i.quantity(),i.unit());}Ingredient(RecipeTemplateIngredient i){this(i.id(),i.productTemplate(),i.quantity(),i.unit());}}
}
