package dk.jamesbabz.madkursus.service.applications;

import java.math.BigDecimal;
import dk.jamesbabz.madkursus.service.models.*;
import org.springframework.stereotype.Component;

@Component
public class RecipeQuantityNormalizer {
    public NormalizedRecipeQuantity normalize(BigDecimal value, RecipeUnit recipeUnit, Unit storage) {
        if(storage==Unit.GRAM && recipeUnit==RecipeUnit.GRAM) return new NormalizedRecipeQuantity(value,Unit.GRAM,null);
        if(storage==Unit.PIECE && recipeUnit==RecipeUnit.PIECE) return new NormalizedRecipeQuantity(value,Unit.PIECE,null);
        if(storage==Unit.MILLILITER) {
            var milliliters=recipeUnit.toMilliliters(value);
            if(milliliters.isPresent()) return new NormalizedRecipeQuantity(milliliters.get(),Unit.MILLILITER,null);
        }
        return new NormalizedRecipeQuantity(null,storage,
                "Opskriften bruger "+recipeUnit.name()+", men lageret føres i "+storage.name()+". Kan ikke beregnes automatisk.");
    }
}
