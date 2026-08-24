package dk.jamesbabz.madkursus.service.applications;

import dk.jamesbabz.madkursus.service.models.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class RecipeQuantityNormalizerTest {
    private final RecipeQuantityNormalizer normalizer=new RecipeQuantityNormalizer();
    private final ProductTemplateUnitConversion flourRule=new ProductTemplateUnitConversion(RecipeUnit.TABLESPOON,RecipeUnit.GRAM,new BigDecimal("9"));

    @Test void genericVolumeUnitsConvertExactlyToMilliliters(){ProductTemplate oil=template(Unit.MILLILITER,List.of());assertThat(normalizer.normalize(BigDecimal.ONE,RecipeUnit.TABLESPOON,oil).quantity()).isEqualByComparingTo("15");assertThat(normalizer.normalize(BigDecimal.ONE,RecipeUnit.TEASPOON,oil).quantity()).isEqualByComparingTo("5");assertThat(normalizer.normalize(BigDecimal.ONE,RecipeUnit.DECILITER,oil).quantity()).isEqualByComparingTo("100");}
    @Test void productSpecificConversionSupportsForwardInverseAndSimpleGenericChain(){ProductTemplate flour=template(Unit.GRAM,List.of(flourRule));assertThat(normalizer.normalize(BigDecimal.ONE,RecipeUnit.TABLESPOON,flour).quantity()).isEqualByComparingTo("9");assertThat(normalizer.normalize(BigDecimal.ONE,RecipeUnit.TEASPOON,flour).quantity()).isEqualByComparingTo("3");assertThat(normalizer.convert(new BigDecimal("9"),RecipeUnit.GRAM,RecipeUnit.TABLESPOON,List.of(flourRule))).contains(new BigDecimal("1"));}
    @Test void unsupportedCrossDimensionConversionRemainsUnresolved(){ProductTemplate flour=template(Unit.GRAM,List.of());assertThat(normalizer.normalize(BigDecimal.ONE,RecipeUnit.TABLESPOON,flour).resolved()).isFalse();assertThat(normalizer.convert(BigDecimal.ONE,RecipeUnit.TABLESPOON,RecipeUnit.GRAM,List.of())).isEmpty();}
    private ProductTemplate template(Unit unit,List<ProductTemplateUnitConversion> conversions){return new ProductTemplate(UUID.randomUUID(),"Test",ProductCategory.BAKING,unit,InventoryTrackingMode.QUANTITY,List.of(),false,conversions);}
}
