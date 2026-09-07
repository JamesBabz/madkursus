package dk.jamesbabz.madkursus.outbound.dtunutrition;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dtu_reference_foods")
@IdClass(DtuReferenceFoodId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DtuReferenceFoodEntity {
    @Id private String datasetVersion;
    @Id private String foodId;
    private String danishName;
    private String normalizedName;
    private BigDecimal carbohydrateGrams;
    private BigDecimal basisQuantity;
    private String basisUnit;
    private String stateDescription;
    private LocalDate sourceDate;
    private String sourceUrl;
    private Instant importedAt;

    public DtuReferenceFoodEntity(String datasetVersion, String foodId, String danishName,
                                  String normalizedName, BigDecimal carbohydrateGrams,
                                  String stateDescription, LocalDate sourceDate, String sourceUrl) {
        this.datasetVersion = datasetVersion;
        this.foodId = foodId;
        this.danishName = danishName;
        this.normalizedName = normalizedName;
        this.carbohydrateGrams = carbohydrateGrams;
        this.basisQuantity = new BigDecimal("100");
        this.basisUnit = "GRAM";
        this.stateDescription = stateDescription;
        this.sourceDate = sourceDate;
        this.sourceUrl = sourceUrl;
        this.importedAt = Instant.now();
    }
}
