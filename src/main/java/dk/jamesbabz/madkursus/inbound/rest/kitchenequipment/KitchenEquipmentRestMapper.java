package dk.jamesbabz.madkursus.inbound.rest.kitchenequipment;

import java.time.ZoneOffset;
import java.util.*;
import dk.jamesbabz.madkursus.inbound.rest.dto.*;
import dk.jamesbabz.madkursus.service.applications.KitchenEquipmentService.Input;
import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.models.KitchenEquipment.*;
import org.springframework.stereotype.Component;

@Component
public class KitchenEquipmentRestMapper {
    public Input input(KitchenEquipmentInputDTO d) {
        Map<HeatLevel,String> heat=d.getHeatMappings()==null?null:new EnumMap<>(HeatLevel.class);
        if(heat!=null) d.getHeatMappings().forEach((key,value)->heat.put(HeatLevel.valueOf(key),value));
        return new Input(EquipmentType.valueOf(d.getEquipmentType().name()),d.getName(),d.getActive(),d.getPreferred(),
                d.getHeatSource()==null?null:HeatSource.valueOf(d.getHeatSource().name()),d.getMinimumLevel(),d.getMaximumLevel(),heat,
                d.getOvenModes()==null?Set.of():d.getOvenModes().stream().map(v->OvenMode.valueOf(v.name())).collect(java.util.stream.Collectors.toSet()),
                d.getMinimumTemperatureCelsius(),d.getMaximumTemperatureCelsius(),d.getCapacityMl(),d.getDiameterMm(),d.getNonStick(),
                d.getThermometerType()==null?null:ThermometerType.valueOf(d.getThermometerType().name()),d.getMaxPowerWatts());
    }
    public KitchenEquipmentDTO dto(KitchenEquipment e) {
        KitchenEquipmentDTO d=new KitchenEquipmentDTO(EquipmentTypeDTO.valueOf(e.equipmentType().name()),e.name(),e.active(),e.preferred(),e.id(),e.createdAt().atOffset(ZoneOffset.UTC),e.updatedAt().atOffset(ZoneOffset.UTC));
        switch(e.configuration()) {
            case Stove c -> d.heatSource(HeatSourceDTO.valueOf(c.heatSource().name())).minimumLevel(c.minimumLevel()).maximumLevel(c.maximumLevel())
                    .heatMappings(c.heatMappings().entrySet().stream().collect(java.util.stream.Collectors.toMap(x->x.getKey().name(),Map.Entry::getValue)));
            case Oven c -> d.ovenModes(c.ovenModes().stream().map(x->OvenModeDTO.valueOf(x.name())).collect(java.util.stream.Collectors.toSet()))
                    .minimumTemperatureCelsius(c.minimumTemperatureCelsius()).maximumTemperatureCelsius(c.maximumTemperatureCelsius());
            case Pot c -> d.capacityMl(c.capacityMl());
            case Pan c -> d.diameterMm(c.diameterMm()).nonStick(c.nonStick());
            case AirFryer c -> d.capacityMl(c.capacityMl()).minimumTemperatureCelsius(c.minimumTemperatureCelsius()).maximumTemperatureCelsius(c.maximumTemperatureCelsius());
            case Thermometer c -> d.thermometerType(c.thermometerType()==null?null:ThermometerTypeDTO.valueOf(c.thermometerType().name()));
            case Microwave c -> d.maxPowerWatts(c.maxPowerWatts());
        }
        return d;
    }
}
