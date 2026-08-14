package dk.jamesbabz.madkursus.service.applications;

import java.time.Instant;
import java.util.*;
import dk.jamesbabz.madkursus.service.exceptions.*;
import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.models.KitchenEquipment.*;
import dk.jamesbabz.madkursus.service.ports.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class KitchenEquipmentService {
    private final KitchenEquipmentPort port;
    private final CurrentUserProvider currentUser;

    public record Input(EquipmentType equipmentType, String name, Boolean active, Boolean preferred,
            HeatSource heatSource, Integer minimumLevel, Integer maximumLevel, Map<HeatLevel,String> heatMappings,
            Set<OvenMode> ovenModes, Integer minimumTemperatureCelsius, Integer maximumTemperatureCelsius,
            Integer capacityMl, Integer diameterMm, Boolean nonStick, ThermometerType thermometerType,
            Integer maxPowerWatts) {}

    public List<KitchenEquipment> getAll() { return port.findAllByUserId(currentUser.currentUserId()); }
    public KitchenEquipment get(UUID id) { return port.findByIdAndUserId(id,currentUser.currentUserId())
            .orElseThrow(()->new ResourceNotFoundException("Kitchen equipment",id)); }
    public Optional<KitchenEquipment> findPreferred(EquipmentType type) {
        return port.findPreferredByUserIdAndType(currentUser.currentUserId(),type);
    }
    public Optional<KitchenEquipment> findPreferredStove() { return findPreferred(EquipmentType.STOVE); }
    public List<KitchenEquipment> findPots() { return byType(EquipmentType.POT); }
    public List<KitchenEquipment> findPans() { return byType(EquipmentType.PAN); }
    public String resolveHeatSetting(KitchenEquipment stove, HeatLevel heat) {
        if (stove.equipmentType()!=EquipmentType.STOVE || !(stove.configuration() instanceof Stove profile))
            throw new InvalidInputException("Equipment is not a stove");
        String result=profile.heatMappings().get(heat);
        if (result==null || result.isBlank()) throw new InvalidInputException("Stove heat mapping is incomplete");
        return result;
    }

    @Transactional public KitchenEquipment create(Input input) { return persist(null,null,input); }
    @Transactional public KitchenEquipment update(UUID id, Input input) { return persist(id,get(id),input); }
    @Transactional public void delete(UUID id) { get(id); port.deleteByIdAndUserId(id,currentUser.currentUserId()); }

    private List<KitchenEquipment> byType(EquipmentType type) {
        return getAll().stream().filter(e->e.equipmentType()==type && e.active()).toList();
    }
    private KitchenEquipment persist(UUID id, KitchenEquipment old, Input input) {
        if(input==null || input.equipmentType()==null) throw new InvalidInputException("Equipment type is required");
        if(old!=null && old.equipmentType()!=input.equipmentType()) throw new InvalidInputException("Equipment type cannot be changed");
        if(input.name()==null || input.name().isBlank()) throw new InvalidInputException("Equipment name is required");
        UUID user=currentUser.currentUserId(); String name=input.name().trim();
        if(port.existsByUserIdAndTypeAndName(user,input.equipmentType(),name,id))
            throw new ConflictException("Equipment with this name and type already exists");
        Configuration configuration=configuration(input);
        boolean preferred=Boolean.TRUE.equals(input.preferred());
        if(preferred) port.clearPreferred(user,input.equipmentType(),id);
        Instant now=Instant.now();
        return port.save(new KitchenEquipment(id,user,input.equipmentType(),name,
                input.active()==null || input.active(),preferred,configuration,old==null?now:old.createdAt(),now));
    }
    private Configuration configuration(Input i) {
        return switch(i.equipmentType()) {
            case STOVE -> stove(i);
            case OVEN -> new Oven(i.ovenModes()==null?Set.of():Set.copyOf(i.ovenModes()),
                    temperatureRange(i.minimumTemperatureCelsius(),i.maximumTemperatureCelsius())[0],temperatureRange(i.minimumTemperatureCelsius(),i.maximumTemperatureCelsius())[1]);
            case POT -> new Pot(positive(i.capacityMl(),"Capacity"));
            case PAN -> new Pan(positive(i.diameterMm(),"Diameter"),i.nonStick());
            case AIR_FRYER -> new AirFryer(positive(i.capacityMl(),"Capacity"),
                    temperatureRange(i.minimumTemperatureCelsius(),i.maximumTemperatureCelsius())[0],temperatureRange(i.minimumTemperatureCelsius(),i.maximumTemperatureCelsius())[1]);
            case THERMOMETER -> new Thermometer(i.thermometerType());
            case MICROWAVE -> new Microwave(positive(i.maxPowerWatts(),"Maximum power"));
        };
    }
    private Stove stove(Input i) {
        HeatSource source=Objects.requireNonNullElse(i.heatSource(),HeatSource.OTHER);
        Integer min=i.minimumLevel(),max=i.maximumLevel();
        boolean numeric=min!=null || max!=null || source!=HeatSource.GAS;
        if(numeric && (min==null || max==null || min<0 || min>max)) throw new InvalidInputException("Valid minimum and maximum stove levels are required");
        Map<HeatLevel,String> mappings=i.heatMappings()==null || i.heatMappings().isEmpty()
                ? defaultMappings(source,min,max) : new EnumMap<>(i.heatMappings());
        for(HeatLevel heat:HeatLevel.values()) {
            String value=mappings.get(heat);
            if(value==null || value.isBlank()) throw new InvalidInputException("All stove heat levels must be mapped");
            if(numeric) try {
                int level=Integer.parseInt(value);
                if(level<min || level>max) throw new InvalidInputException("Heat mappings must be between minimum and maximum level");
            } catch(NumberFormatException e) { throw new InvalidInputException("Numeric stoves require numeric heat mappings"); }
        }
        return new Stove(source,min,max,Map.copyOf(mappings));
    }
    public Map<HeatLevel,String> defaultMappings(HeatSource source,Integer min,Integer max) {
        EnumMap<HeatLevel,String> values=new EnumMap<>(HeatLevel.class);
        if(source==HeatSource.GAS && min==null && max==null) {
            values.put(HeatLevel.LOW,"Lavt blus"); values.put(HeatLevel.MEDIUM_LOW,"Middel-lavt blus");
            values.put(HeatLevel.MEDIUM,"Middel blus"); values.put(HeatLevel.MEDIUM_HIGH,"Middel-højt blus");
            values.put(HeatLevel.HIGH,"Højt blus"); values.put(HeatLevel.MAX,"Fuldt blus"); return values;
        }
        if(min==null || max==null || min>max) throw new InvalidInputException("Valid minimum and maximum stove levels are required");
        double[] positions={.15,.30,.50,.70,.85,1}; HeatLevel[] levels=HeatLevel.values();
        for(int n=0;n<levels.length;n++) values.put(levels[n],String.valueOf(Math.max(min,Math.min(max,(int)Math.round(min+(max-min)*positions[n])))));
        values.put(HeatLevel.MAX,String.valueOf(max)); return values;
    }
    private Integer positive(Integer value,String field) {
        if(value!=null && value<=0) throw new InvalidInputException(field+" must be positive"); return value;
    }
    private Integer[] temperatureRange(Integer minimum,Integer maximum) {
        minimum=positive(minimum,"Minimum temperature"); maximum=positive(maximum,"Maximum temperature");
        if(minimum!=null && maximum!=null && minimum>maximum) throw new InvalidInputException("Minimum temperature cannot exceed maximum temperature");
        return new Integer[]{minimum,maximum};
    }
}
