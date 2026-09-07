package dk.jamesbabz.madkursus.outbound.dtunutrition;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_template_dtu_mappings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductTemplateDtuMappingEntity {
    @Id private UUID productTemplateId;
    private String datasetVersion;
    private String foodId;
    private BigDecimal approvedCarbohydrateGrams;
    private Instant approvedAt;

    public ProductTemplateDtuMappingEntity(UUID productTemplateId, String datasetVersion, String foodId,
                                           BigDecimal approvedCarbohydrateGrams) {
        this.productTemplateId = productTemplateId;
        this.datasetVersion = datasetVersion;
        this.foodId = foodId;
        this.approvedCarbohydrateGrams = approvedCarbohydrateGrams;
        this.approvedAt = Instant.now();
    }
}
