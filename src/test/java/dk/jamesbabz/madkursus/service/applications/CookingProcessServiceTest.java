package dk.jamesbabz.madkursus.service.applications;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import dk.jamesbabz.madkursus.service.exceptions.InvalidInputException;
import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.models.KitchenEquipment.Stove;
import dk.jamesbabz.madkursus.service.ports.CookingProcessPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CookingProcessServiceTest {
    @Mock CookingProcessPort port;
    @Mock KitchenEquipmentService equipment;
    CookingProcessService service;

    @BeforeEach void setup() { service = new CookingProcessService(port, equipment); lenient().when(equipment.getAll()).thenReturn(List.of()); }

    @Test void validatesUniqueKeysAndAllTemplatePlaceholders() {
        CookingProcess valid=process(List.of(parameter("ITEM", CookingProcessParameterType.TEXT, true, null, 1)), "Brug {ITEM}.", "Færdig med {ITEM}.");
        assertThatCode(()->service.validateDefinition(valid)).doesNotThrowAnyException();
        assertThatThrownBy(()->service.validateDefinition(process(List.of(parameter("ITEM",CookingProcessParameterType.TEXT,true,null,1),parameter("ITEM",CookingProcessParameterType.TEXT,false,null,2)),"Brug {ITEM}.","Færdig."))).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(()->service.validateDefinition(process(valid.parameters(),"Brug {TYPO}.","Færdig."))).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(()->service.validateDefinition(process(valid.parameters(),"Brug {ITEM}.","Færdig med {TYPO}."))).isInstanceOf(InvalidInputException.class);
    }

    @Test void validatesRequiredBindingsAndAcceptsDefaults() {
        CookingProcess process=process(List.of(
                parameter("ITEM",CookingProcessParameterType.INGREDIENT_QUANTITY,true,null,1),
                parameter("TIME",CookingProcessParameterType.DURATION,true,new CookingProcessValue(null,null,60,null,null,null,null),2)),
                "Brug {ITEM} i {TIME}.","Kontrollér {ITEM}.");
        assertThatThrownBy(()->service.validateBindings(process,List.of())).isInstanceOf(InvalidInputException.class);
        ProductTemplate product=product("Kartofler");
        var item=new CookingProcessBinding(null,"ITEM",UUID.randomUUID(),product,new CookingProcessValue(new BigDecimal("500"),RecipeUnit.GRAM,null,null,null,null,null));
        assertThatCode(()->service.validateBindings(process,List.of(item))).doesNotThrowAnyException();
    }

    @Test void rendersTypedValuesAndConfiguredStoveMappings() {
        CookingProcess process=process(List.of(
                parameter("ITEM",CookingProcessParameterType.INGREDIENT_QUANTITY,true,null,1),
                parameter("TIME",CookingProcessParameterType.DURATION,true,null,2),
                parameter("TEMP",CookingProcessParameterType.TEMPERATURE,true,null,3),
                parameter("HEAT",CookingProcessParameterType.HEAT_LEVEL,true,null,4)),
                "Kog {ITEM} i {TIME} på {HEAT}.","Færdig ved {TEMP}.");
        when(port.findById(process.id())).thenReturn(Optional.of(process));
        KitchenEquipment stove=new KitchenEquipment(UUID.randomUUID(),UUID.randomUUID(),EquipmentType.STOVE,"Komfur",true,true,new Stove(HeatSource.INDUCTION,1,9,Map.of(HeatLevel.MAX,"9",HeatLevel.LOW,"2")),Instant.now(),Instant.now());
        when(equipment.findPreferredStove()).thenReturn(Optional.of(stove)); when(equipment.resolveHeatSetting(stove,HeatLevel.MAX)).thenReturn("9");
        var bindings=List.of(
                binding("ITEM",product("Kartofler"),new CookingProcessValue(new BigDecimal("500"),RecipeUnit.GRAM,null,null,null,null,null)),
                binding("TIME",null,new CookingProcessValue(null,null,900,null,null,null,null)),
                binding("TEMP",null,new CookingProcessValue(null,null,null,75,null,null,null)),
                binding("HEAT",null,new CookingProcessValue(null,null,null,null,HeatLevel.MAX,null,null)));
        RenderedCookingProcess rendered=service.render(process.id(),bindings);
        assertThat(rendered.instructions()).containsExactly("Kog Kartofler (500 g) i 15 minutter på trin 9.");
        assertThat(rendered.completionCriterion()).isEqualTo("Færdig ved 75 °C.");
        var ninetySeconds=bindings.stream().map(value->value.parameterKey().equals("TIME")
                ?binding("TIME",null,new CookingProcessValue(null,null,90,null,null,null,null)):value).toList();
        assertThat(service.render(process.id(),ninetySeconds).instructions())
                .containsExactly("Kog Kartofler (500 g) i 1 minut og 30 sekunder på trin 9.");
    }

    @Test void fallsBackToAbstractHeatWhenNoStoveExistsAndRendersLowMapping() {
        CookingProcess process=process(List.of(parameter("HEAT",CookingProcessParameterType.HEAT_LEVEL,true,null,1)),"Brug {HEAT}.","Kontrollér.");
        when(port.findById(process.id())).thenReturn(Optional.of(process)); when(equipment.findPreferredStove()).thenReturn(Optional.empty());
        assertThat(service.render(process.id(),List.of(binding("HEAT",null,new CookingProcessValue(null,null,null,null,HeatLevel.MAX,null,null)))).instructions()).containsExactly("Brug maksimal varme.");
        KitchenEquipment stove=new KitchenEquipment(UUID.randomUUID(),UUID.randomUUID(),EquipmentType.STOVE,"Komfur",true,true,new Stove(HeatSource.INDUCTION,1,9,Map.of()),Instant.now(),Instant.now());
        when(equipment.findPreferredStove()).thenReturn(Optional.of(stove)); when(equipment.resolveHeatSetting(stove,HeatLevel.LOW)).thenReturn("2");
        assertThat(service.render(process.id(),List.of(binding("HEAT",null,new CookingProcessValue(null,null,null,null,HeatLevel.LOW,null,null)))).instructions()).containsExactly("Brug trin 2.");
    }

    private CookingProcess process(List<CookingProcessParameter> parameters,String step,String completion){var now=Instant.now();return new CookingProcess(UUID.randomUUID(),"TEST","Test",null,true,now,now,parameters,List.of(new CookingProcessStep(UUID.randomUUID(),step,1)),List.of(),completion);}
    private CookingProcessParameter parameter(String key,CookingProcessParameterType type,boolean required,CookingProcessValue value,int order){return new CookingProcessParameter(UUID.randomUUID(),key,key,type,required,value,null,order);}
    private CookingProcessBinding binding(String key,ProductTemplate product,CookingProcessValue value){return new CookingProcessBinding(UUID.randomUUID(),key,product==null?null:UUID.randomUUID(),product,value);}
    private ProductTemplate product(String name){return new ProductTemplate(UUID.randomUUID(),name,ProductCategory.VEGETABLE,Unit.GRAM,InventoryTrackingMode.QUANTITY,List.of(),true);}
}
