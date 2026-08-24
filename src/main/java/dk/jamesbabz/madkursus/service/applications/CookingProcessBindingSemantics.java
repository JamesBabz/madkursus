package dk.jamesbabz.madkursus.service.applications;

import dk.jamesbabz.madkursus.service.exceptions.InvalidInputException;
import dk.jamesbabz.madkursus.service.models.CookingProcessParameterType;
import dk.jamesbabz.madkursus.service.models.CookingProcessValue;
import dk.jamesbabz.madkursus.service.models.RecipeUnit;

/** Shared semantic validation for persisted and authoring-time process bindings. */
public final class CookingProcessBindingSemantics {
    private CookingProcessBindingSemantics() {}

    public static void validate(CookingProcessParameterType type,String label,boolean hasRecipeIngredient,
            boolean hasProductTemplate,boolean hasPreparedComponent,CookingProcessValue value,RecipeUnit parameterUnit) {
        if(hasPreparedComponent){
            if(type!=CookingProcessParameterType.INGREDIENT_QUANTITY&&type!=CookingProcessParameterType.INGREDIENT_LIST)
                throw new InvalidInputException("Prepared component requires an ingredient process input");
            return;
        }
        if(value==null)throw new InvalidInputException("Parameter value is required: "+label);
        boolean valid=switch(type){
            case INGREDIENT_QUANTITY -> hasRecipeIngredient&&hasProductTemplate&&value.quantity()!=null&&value.quantity().signum()>0&&value.unit()!=null;
            case INGREDIENT_LIST -> false;
            case QUANTITY -> value.quantity()!=null&&value.quantity().signum()>0&&(value.unit()!=null||parameterUnit!=null);
            case DURATION -> value.durationSeconds()!=null&&value.durationSeconds()>0;
            case TEMPERATURE -> value.temperatureCelsius()!=null;
            case HEAT_LEVEL -> value.heatLevel()!=null;
            case NUMBER -> value.number()!=null;
            case TEXT -> value.text()!=null&&!value.text().isBlank();
        };
        if(!valid)throw new InvalidInputException("Invalid value for process parameter: "+label);
    }
}
