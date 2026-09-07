package dk.jamesbabz.madkursus.outbound.producttemplate.mappers;

import java.util.List;
import dk.jamesbabz.madkursus.outbound.producttemplate.details.ProductTemplateEntity;
import dk.jamesbabz.madkursus.service.models.ProductTemplate;
import dk.jamesbabz.madkursus.service.models.ProductTemplateUnitConversion;
import dk.jamesbabz.madkursus.service.models.NutritionData;
import org.springframework.stereotype.Component;

@Component
public class ProductTemplateEntityMapper {
    public ProductTemplate toModel(ProductTemplateEntity entity) {
        return new ProductTemplate(entity.getId(), entity.getName(), entity.getCategory(), entity.getDefaultUnit(),
                entity.getDefaultTrackingMode(), List.copyOf(entity.getAliases()), entity.isCommon(),entity.getConversions().stream()
                .map(value->new ProductTemplateUnitConversion(value.getFromUnit(),value.getToUnit(),value.getFactor())).toList(),
                entity.getCarbohydrateGrams()==null?null:new NutritionData(entity.getCarbohydrateGrams(),entity.getNutritionBasisQuantity(),entity.getNutritionBasisUnit(),entity.getNutritionSource(),entity.getNutritionProvider(),entity.getNutritionExternalFoodId(),entity.getNutritionSourceVersion(),entity.getNutritionSourceUrl(),entity.getNutritionNote()));
    }
}
