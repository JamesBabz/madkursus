package dk.jamesbabz.madkursus.inbound.rest.producttemplate;

import dk.jamesbabz.madkursus.inbound.rest.dto.ProductCategoryDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.ProductTemplateDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.UnitDTO;
import dk.jamesbabz.madkursus.service.models.ProductTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProductTemplateRestMapper {
    public ProductTemplateDTO toDto(ProductTemplate t) {
        return new ProductTemplateDTO(t.id(), t.name(), ProductCategoryDTO.valueOf(t.category().name()),
                UnitDTO.valueOf(t.defaultUnit().name()), t.aliases(), t.common());
    }
}
