package dk.jamesbabz.madkursus.outbound.producttemplate.details;

import java.math.BigDecimal;
import dk.jamesbabz.madkursus.service.models.RecipeUnit;
import jakarta.persistence.*;
import lombok.*;

@Embeddable @Getter @EqualsAndHashCode @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ProductTemplateUnitConversionValue {
    @Enumerated(EnumType.STRING) @Column(name="from_unit") private RecipeUnit fromUnit;
    @Enumerated(EnumType.STRING) @Column(name="to_unit") private RecipeUnit toUnit;
    private BigDecimal factor;
}
