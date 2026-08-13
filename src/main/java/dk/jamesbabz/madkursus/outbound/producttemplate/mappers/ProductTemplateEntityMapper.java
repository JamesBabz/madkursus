package dk.jamesbabz.madkursus.outbound.producttemplate.mappers;

import java.util.List;
import dk.jamesbabz.madkursus.outbound.producttemplate.details.ProductTemplateEntity;
import dk.jamesbabz.madkursus.service.models.ProductTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProductTemplateEntityMapper {
    public ProductTemplate toModel(ProductTemplateEntity entity) {
        return new ProductTemplate(entity.getId(), entity.getName(), entity.getCategory(), entity.getDefaultUnit(),
                entity.getDefaultTrackingMode(), List.copyOf(entity.getAliases()), entity.isCommon());
    }
}
