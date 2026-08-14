package dk.jamesbabz.madkursus.service.applications;

import java.util.*;
import dk.jamesbabz.madkursus.service.applications.KitchenEquipmentService.Input;
import dk.jamesbabz.madkursus.service.exceptions.*;
import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.models.KitchenEquipment.*;
import dk.jamesbabz.madkursus.service.ports.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KitchenEquipmentServiceTest {
    @Mock KitchenEquipmentPort port; @Mock CurrentUserProvider currentUser;
    KitchenEquipmentService service; UUID user;
    @BeforeEach void setup(){service=new KitchenEquipmentService(port,currentUser);user=UUID.randomUUID();lenient().when(currentUser.currentUserId()).thenReturn(user);lenient().when(port.save(any())).thenAnswer(c->c.getArgument(0));}

    @Test void createsNumericStovesWithStoredDefaultsForDifferentRanges(){
        KitchenEquipment nine=service.create(stove("Komfur",1,9,null,true));
        KitchenEquipment twelve=service.create(stove("Ekstra komfur",1,12,null,false));
        Stove a=(Stove)nine.configuration(),b=(Stove)twelve.configuration();
        assertThat(a.heatMappings()).containsEntry(HeatLevel.MAX,"9").containsEntry(HeatLevel.MEDIUM,"5");
        assertThat(b.heatMappings()).containsEntry(HeatLevel.MAX,"12").containsEntry(HeatLevel.MEDIUM,"7");
        assertThat(service.resolveHeatSetting(twelve,HeatLevel.MEDIUM_HIGH)).isEqualTo("9");
        verify(port).clearPreferred(user,EquipmentType.STOVE,null);
    }
    @Test void acceptsEditedMappingAndRejectsOutsideRange(){
        Map<HeatLevel,String> mapping=service.defaultMappings(HeatSource.INDUCTION,1,9);mapping.put(HeatLevel.MEDIUM,"6");
        assertThat(((Stove)service.create(stove("Komfur",1,9,mapping,false)).configuration()).heatMappings()).containsEntry(HeatLevel.MEDIUM,"6");
        mapping.put(HeatLevel.HIGH,"10");
        assertThatThrownBy(()->service.create(stove("Andet",1,9,mapping,false))).isInstanceOf(InvalidInputException.class);
    }
    @Test void supportsDescriptiveGasControls(){
        KitchenEquipment gas=service.create(new Input(EquipmentType.STOVE,"Gaskomfur",true,true,HeatSource.GAS,null,null,null,null,null,null,null,null,null,null,null));
        assertThat(service.resolveHeatSetting(gas,HeatLevel.LOW)).isEqualTo("Lavt blus");
        assertThat(service.resolveHeatSetting(gas,HeatLevel.MAX)).isEqualTo("Fuldt blus");
    }
    @Test void readsUpdatesAndDeletesOnlyThroughCurrentUserScope(){
        UUID id=UUID.randomUUID(); KitchenEquipment own=equipment(id,EquipmentType.POT,new Pot(5000));
        when(port.findByIdAndUserId(id,user)).thenReturn(Optional.of(own));
        assertThat(service.get(id)).isSameAs(own);
        service.update(id,new Input(EquipmentType.POT,"Stor gryde",true,false,null,null,null,null,null,null,null,6000,null,null,null,null));
        service.delete(id);
        verify(port,atLeast(2)).findByIdAndUserId(id,user);verify(port).deleteByIdAndUserId(id,user);
        UUID other=UUID.randomUUID();when(port.findByIdAndUserId(other,user)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.get(other)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(()->service.delete(other)).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void listAndPreferredQueriesAreUserScoped(){service.getAll();service.findPreferredStove();verify(port).findAllByUserId(user);verify(port).findPreferredByUserIdAndType(user,EquipmentType.STOVE);}
    @ParameterizedTest @MethodSource("otherEquipment") void preservesTypeSpecificCapabilities(Input input,Class<?> configurationType){assertThat(service.create(input).configuration()).isInstanceOf(configurationType);}
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> otherEquipment(){return java.util.stream.Stream.of(
            org.junit.jupiter.params.provider.Arguments.of(new Input(EquipmentType.OVEN,"Ovn",true,true,null,null,null,null,Set.of(OvenMode.CONVENTIONAL,OvenMode.FAN),50,275,null,null,null,null,null),Oven.class),
            org.junit.jupiter.params.provider.Arguments.of(new Input(EquipmentType.POT,"Stor gryde",true,false,null,null,null,null,null,null,null,5000,null,null,null,null),Pot.class),
            org.junit.jupiter.params.provider.Arguments.of(new Input(EquipmentType.PAN,"Pande",true,false,null,null,null,null,null,null,null,null,280,true,null,null),Pan.class),
            org.junit.jupiter.params.provider.Arguments.of(new Input(EquipmentType.AIR_FRYER,"Airfryer",true,false,null,null,null,null,null,80,220,5000,null,null,null,null),AirFryer.class),
            org.junit.jupiter.params.provider.Arguments.of(new Input(EquipmentType.THERMOMETER,"Termometer",true,false,null,null,null,null,null,null,null,null,null,null,ThermometerType.PROBE,null),Thermometer.class),
            org.junit.jupiter.params.provider.Arguments.of(new Input(EquipmentType.MICROWAVE,"Mikroovn",true,false,null,null,null,null,null,null,null,null,null,null,null,900),Microwave.class));}
    private Input stove(String name,int min,int max,Map<HeatLevel,String> mapping,boolean preferred){return new Input(EquipmentType.STOVE,name,true,preferred,HeatSource.INDUCTION,min,max,mapping,null,null,null,null,null,null,null,null);}
    private KitchenEquipment equipment(UUID id,EquipmentType type,Configuration config){var now=java.time.Instant.now();return new KitchenEquipment(id,user,type,"Stor gryde",true,false,config,now,now);}
}
