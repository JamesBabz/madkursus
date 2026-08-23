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

    @Test void rendersCommonFractionsAndGrinderTurnsNaturally() {
        CookingProcess process=process(List.of(parameter("PEPPER",CookingProcessParameterType.INGREDIENT_QUANTITY,true,null,1)),"Brug {PEPPER}.","Smag til.");
        when(port.findById(process.id())).thenReturn(Optional.of(process));ProductTemplate pepper=product("Sort peber");
        var half=binding("PEPPER",pepper,new CookingProcessValue(new BigDecimal("0.5"),RecipeUnit.TEASPOON,null,null,null,null,null));
        assertThat(service.render(process.id(),List.of(half)).instructions()).containsExactly("Brug Sort peber (½ tsk).");
        var turns=binding("PEPPER",pepper,new CookingProcessValue(BigDecimal.TEN,RecipeUnit.GRINDER_TURN,null,null,null,null,null));
        assertThat(service.render(process.id(),List.of(turns)).instructions()).containsExactly("Brug Sort peber (10 omgange).");
        var fractional=binding("PEPPER",pepper,new CookingProcessValue(new BigDecimal("7.5"),RecipeUnit.GRINDER_TURN,null,null,null,null,null));
        assertThat(service.render(process.id(),List.of(fractional)).instructions()).containsExactly("Brug Sort peber (8 omgange).");
        var many=binding("PEPPER",pepper,new CookingProcessValue(new BigDecimal("22.5"),RecipeUnit.GRINDER_TURN,null,null,null,null,null));
        assertThat(service.render(process.id(),List.of(many)).instructions()).containsExactly("Brug Sort peber (23 omgange).");
    }

    @Test void derivesPastaWaterAndSaltAndAllowsSparseOverrides() {
        var pasta=parameter("PASTA",CookingProcessParameterType.INGREDIENT_QUANTITY,true,null,1);
        var water=new CookingProcessParameter(UUID.randomUUID(),"WATER","Vand",CookingProcessParameterType.QUANTITY,false,null,RecipeUnit.MILLILITER,2,CookingProcessParameterSource.DERIVED,CookingProcessDerivedRule.PASTA_WATER_PER_GRAM,"PASTA");
        var salt=new CookingProcessParameter(UUID.randomUUID(),"SALT","Salt",CookingProcessParameterType.QUANTITY,false,null,RecipeUnit.TEASPOON,3,CookingProcessParameterSource.DERIVED,CookingProcessDerivedRule.PASTA_SALT_PER_GRAM,"PASTA");
        CookingProcess process=process(List.of(pasta,water,salt),"Kog {PASTA} i {WATER} med {SALT}.","Al dente.");when(port.findById(process.id())).thenReturn(Optional.of(process));
        var input=binding("PASTA",product("Penne"),new CookingProcessValue(new BigDecimal("200"),RecipeUnit.GRAM,null,null,null,null,null));
        assertThat(service.render(process.id(),List.of(input)).instructions()).containsExactly("Kog Penne (200 g) i 2 liter med 2 tsk.");
        var override=binding("WATER",null,new CookingProcessValue(new BigDecimal("1500"),RecipeUnit.MILLILITER,null,null,null,null,null));
        assertThat(service.render(process.id(),List.of(input,override)).instructions()).containsExactly("Kog Penne (200 g) i 1.500 ml med 2 tsk.");
    }

    @Test void potatoesScaleDerivedConsumablesButKeepProcessTimeAndHeatStatic() {
        var potatoes=parameter("POTATOES",CookingProcessParameterType.INGREDIENT_QUANTITY,true,null,1);
        var water=new CookingProcessParameter(UUID.randomUUID(),"WATER","Vand",CookingProcessParameterType.QUANTITY,false,null,RecipeUnit.MILLILITER,2,CookingProcessParameterSource.DERIVED,CookingProcessDerivedRule.POTATO_WATER_PER_GRAM,"POTATOES");
        var salt=new CookingProcessParameter(UUID.randomUUID(),"SALT","Salt",CookingProcessParameterType.QUANTITY,false,null,RecipeUnit.TEASPOON,3,CookingProcessParameterSource.DERIVED,CookingProcessDerivedRule.POTATO_SALT_PER_GRAM,"POTATOES");
        var heat=new CookingProcessParameter(UUID.randomUUID(),"HEAT","Varme",CookingProcessParameterType.HEAT_LEVEL,false,new CookingProcessValue(null,null,null,null,HeatLevel.MAX,null,null),null,4,CookingProcessParameterSource.DEFAULT,null,null);
        var time=new CookingProcessParameter(UUID.randomUUID(),"TIME","Tid",CookingProcessParameterType.DURATION,false,new CookingProcessValue(null,null,900,null,null,null,null),null,5,CookingProcessParameterSource.OVERRIDEABLE_DEFAULT,null,null);
        CookingProcess process=process(List.of(potatoes,water,salt,heat,time),"Brug {WATER}, {SALT}, {HEAT} og {TIME}.","Møre.");when(port.findById(process.id())).thenReturn(Optional.of(process));when(equipment.findPreferredStove()).thenReturn(Optional.empty());
        var one=binding("POTATOES",product("Kartofler"),new CookingProcessValue(new BigDecimal("500"),RecipeUnit.GRAM,null,null,null,null,null));
        assertThat(service.render(process.id(),List.of(one)).instructions()).containsExactly("Brug 1.500 ml, ½ tsk, maksimal varme og 15 minutter.");
        var two=binding("POTATOES",product("Kartofler"),new CookingProcessValue(new BigDecimal("1000"),RecipeUnit.GRAM,null,null,null,null,null));
        assertThat(service.render(process.id(),List.of(two)).instructions()).containsExactly("Brug 3 liter, 1 tsk, maksimal varme og 15 minutter.");
        var override=binding("TIME",null,new CookingProcessValue(null,null,1080,null,null,null,null));
        assertThat(service.render(process.id(),List.of(one,override)).instructions()).containsExactly("Brug 1.500 ml, ½ tsk, maksimal varme og 18 minutter.");
    }

    @Test void resolvesStructuredTimingAndConcretePreparationWithoutScalingTime() {
        var potatoes=parameter("POTATOES",CookingProcessParameterType.INGREDIENT_QUANTITY,true,null,1);
        var time=new CookingProcessParameter(UUID.randomUUID(),"TIME","Tid",CookingProcessParameterType.DURATION,false,new CookingProcessValue(null,null,900,null,null,null,null),null,2,CookingProcessParameterSource.OVERRIDEABLE_DEFAULT,null,null);
        var now=Instant.now();CookingProcess process=new CookingProcess(UUID.randomUUID(),"TIMED","Kog kartofler",null,true,now,now,List.of(potatoes,time),List.of(new CookingProcessStep(UUID.randomUUID(),"Kog {POTATOES} i {TIME}.",1)),List.of(),"Møre.",300,null,null,"TIME",List.of(new CookingProcessPreparationRequirement(UUID.randomUUID(),"POTATOES","Mål {POTATOES} op.",1)));
        when(port.findById(process.id())).thenReturn(Optional.of(process));
        var input=binding("POTATOES",product("Kartofler"),new CookingProcessValue(new BigDecimal("500"),RecipeUnit.GRAM,null,null,null,null,null));
        RenderedCookingProcess rendered=service.render(process.id(),List.of(input));
        assertThat(rendered.durationSummary()).isEqualTo("5 min aktiv · 15 min ventetid");
        assertThat(rendered.preparationInstructions()).containsExactly("Mål Kartofler (500 g) op.");
        var scaled=binding("POTATOES",product("Kartofler"),new CookingProcessValue(new BigDecimal("1000"),RecipeUnit.GRAM,null,null,null,null,null));
        assertThat(service.render(process.id(),List.of(scaled)).durationSummary()).isEqualTo("5 min aktiv · 15 min ventetid");
        var override=binding("TIME",null,new CookingProcessValue(null,null,1080,null,null,null,null));
        assertThat(service.render(process.id(),List.of(input,override)).durationSummary()).isEqualTo("5 min aktiv · 18 min ventetid");
        assertThat(service.durationSummary(120,0)).isEqualTo("2 min aktiv");
        assertThat(service.durationSummary(0,0)).isEmpty();
    }

    @Test void compositionPreparationIncludesOnlySelectedMembersAndDeduplicates() {
        var base=parameter("BASE",CookingProcessParameterType.INGREDIENT_QUANTITY,true,null,1);
        var additions=new CookingProcessParameter(UUID.randomUUID(),"ADDITIONS","Tilføjelser",CookingProcessParameterType.INGREDIENT_LIST,false,null,null,2,CookingProcessParameterSource.INPUT,null,null);
        var now=Instant.now();CookingProcess process=new CookingProcess(UUID.randomUUID(),"MIX","Rør fars",null,true,now,now,List.of(base,additions),List.of(new CookingProcessStep(UUID.randomUUID(),"Kom {BASE} og {ADDITIONS} i skålen.",1)),List.of(),"Ensartet.",120,0,null,null,List.of(new CookingProcessPreparationRequirement(UUID.randomUUID(),"ADDITIONS","Mål {ADDITIONS} op.",1),new CookingProcessPreparationRequirement(UUID.randomUUID(),"ADDITIONS","Mål {ADDITIONS} op.",2)));
        when(port.findById(process.id())).thenReturn(Optional.of(process));
        var meat=binding("BASE",product("Hakket oksekød"),new CookingProcessValue(new BigDecimal("400"),RecipeUnit.GRAM,null,null,null,null,null));
        var onion=binding("ADDITIONS:onion",product("Løg"),new CookingProcessValue(new BigDecimal("0.5"),RecipeUnit.PIECE,null,null,null,null,null));
        assertThat(service.render(process.id(),List.of(meat,onion)).preparationInstructions()).containsExactly("Mål Løg (½ stk) op.");
    }
    @Test void preparedComponentRendersConcreteContentsAndCompactName() {
        var input=parameter("MIX",CookingProcessParameterType.INGREDIENT_QUANTITY,true,null,1);var now=Instant.now();CookingProcess process=new CookingProcess(UUID.randomUUID(),"COMPONENT","Rør fars",null,true,now,now,List.of(input),List.of(new CookingProcessStep(UUID.randomUUID(),"Kom {MIX} i en skål.",1)),List.of(),"Ensartet.");when(port.findById(process.id())).thenReturn(Optional.of(process));
        PreparedComponent component=new PreparedComponent(UUID.randomUUID(),"MIX","Ingredienser til fars",1,List.of(new PreparedComponentIngredient(UUID.randomUUID(),UUID.randomUUID(),product("Hakket oksekød"),new BigDecimal("400"),RecipeUnit.GRAM,1),new PreparedComponentIngredient(UUID.randomUUID(),UUID.randomUUID(),product("Løg"),new BigDecimal("0.5"),RecipeUnit.PIECE,2)),List.of());
        CookingProcessBinding binding=new CookingProcessBinding(UUID.randomUUID(),"MIX",null,null,null,component.id(),component);RenderedCookingProcess rendered=service.render(process.id(),List.of(binding));
        assertThat(rendered.instructions()).containsExactly("Kom Hakket oksekød (400 g) og Løg (½ stk) i en skål.");assertThat(rendered.inputSummary()).isEqualTo("Ingredienser til fars");
    }

    @Test void rendersDerivedIngredientListAndReadableLitres() {
        CookingProcess process=process(List.of(
                parameter("INGREDIENTS",CookingProcessParameterType.INGREDIENT_LIST,true,null,1),
                parameter("MEAT",CookingProcessParameterType.INGREDIENT_QUANTITY,true,null,2),
                parameter("ONION",CookingProcessParameterType.INGREDIENT_QUANTITY,false,null,3),
                parameter("WATER",CookingProcessParameterType.QUANTITY,true,null,4)),
                "Kom {INGREDIENTS} i skålen med {WATER}.","Kontrollér.");
        when(port.findById(process.id())).thenReturn(Optional.of(process));
        var bindings=List.of(
                binding("MEAT",product("Hakket oksekød"),new CookingProcessValue(new BigDecimal("400"),RecipeUnit.GRAM,null,null,null,null,null)),
                binding("ONION",product("Løg"),new CookingProcessValue(new BigDecimal("0.5"),RecipeUnit.PIECE,null,null,null,null,null)),
                binding("WATER",null,new CookingProcessValue(new BigDecimal("2000"),RecipeUnit.MILLILITER,null,null,null,null,null)));
        assertThat(service.render(process.id(),bindings).instructions())
                .containsExactly("Kom hakket oksekød og løg i skålen med 2 liter.");
    }

    private CookingProcess process(List<CookingProcessParameter> parameters,String step,String completion){var now=Instant.now();return new CookingProcess(UUID.randomUUID(),"TEST","Test",null,true,now,now,parameters,List.of(new CookingProcessStep(UUID.randomUUID(),step,1)),List.of(),completion);}
    private CookingProcessParameter parameter(String key,CookingProcessParameterType type,boolean required,CookingProcessValue value,int order){return new CookingProcessParameter(UUID.randomUUID(),key,key,type,required,value,null,order);}
    private CookingProcessBinding binding(String key,ProductTemplate product,CookingProcessValue value){return new CookingProcessBinding(UUID.randomUUID(),key,product==null?null:UUID.randomUUID(),product,value);}
    private ProductTemplate product(String name){return new ProductTemplate(UUID.randomUUID(),name,ProductCategory.VEGETABLE,Unit.GRAM,InventoryTrackingMode.QUANTITY,List.of(),true);}
}
