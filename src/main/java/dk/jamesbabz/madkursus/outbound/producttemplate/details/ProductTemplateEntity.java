package dk.jamesbabz.madkursus.outbound.producttemplate.details;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.math.BigDecimal;
import dk.jamesbabz.madkursus.service.models.RecipeUnit;
import dk.jamesbabz.madkursus.service.models.ProductCategory;
import dk.jamesbabz.madkursus.service.models.Unit;
import dk.jamesbabz.madkursus.service.models.InventoryTrackingMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name="product_templates") @Getter @NoArgsConstructor(access=lombok.AccessLevel.PROTECTED)
public class ProductTemplateEntity {
    @Id private UUID id;
    private String name;
    private String normalizedName;
    @Enumerated(EnumType.STRING) private ProductCategory category;
    @Enumerated(EnumType.STRING) private Unit defaultUnit;
    @Enumerated(EnumType.STRING) private InventoryTrackingMode defaultTrackingMode;
    private boolean common;
    private BigDecimal carbohydrateGrams;
    private BigDecimal nutritionBasisQuantity;
    @Enumerated(EnumType.STRING) private RecipeUnit nutritionBasisUnit;
    private String nutritionSource;
    private String nutritionProvider;
    private String nutritionExternalFoodId;
    private String nutritionSourceVersion;
    private String nutritionSourceUrl;
    private String nutritionNote;
    public void updateNutrition(BigDecimal grams,BigDecimal basisQuantity,RecipeUnit basisUnit,String source,String provider,String externalFoodId,String sourceVersion,String sourceUrl,String note){this.carbohydrateGrams=grams;this.nutritionBasisQuantity=basisQuantity;this.nutritionBasisUnit=basisUnit;this.nutritionSource=source;this.nutritionProvider=provider;this.nutritionExternalFoodId=externalFoodId;this.nutritionSourceVersion=sourceVersion;this.nutritionSourceUrl=sourceUrl;this.nutritionNote=note;}
    @ElementCollection(fetch=FetchType.EAGER)
    @CollectionTable(name="product_template_aliases", joinColumns=@JoinColumn(name="template_id"))
    @Column(name="alias")
    private Set<String> aliases = new LinkedHashSet<>();
    @ElementCollection(fetch=FetchType.EAGER)
    @CollectionTable(name="product_template_unit_conversions",joinColumns=@JoinColumn(name="template_id"))
    private Set<ProductTemplateUnitConversionValue> conversions=new LinkedHashSet<>();
}
