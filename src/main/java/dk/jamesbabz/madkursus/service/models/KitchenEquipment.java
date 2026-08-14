package dk.jamesbabz.madkursus.service.models;

import java.time.Instant;
import java.util.*;

public record KitchenEquipment(UUID id, UUID userId, EquipmentType equipmentType, String name,
        boolean active, boolean preferred, Configuration configuration, Instant createdAt, Instant updatedAt) {

    public sealed interface Configuration permits Stove, Oven, Pot, Pan, AirFryer, Thermometer, Microwave {}
    public record Stove(HeatSource heatSource, Integer minimumLevel, Integer maximumLevel,
                        Map<HeatLevel,String> heatMappings) implements Configuration {}
    public record Oven(Set<OvenMode> ovenModes, Integer minimumTemperatureCelsius,
                       Integer maximumTemperatureCelsius) implements Configuration {}
    public record Pot(Integer capacityMl) implements Configuration {}
    public record Pan(Integer diameterMm, Boolean nonStick) implements Configuration {}
    public record AirFryer(Integer capacityMl, Integer minimumTemperatureCelsius,
                           Integer maximumTemperatureCelsius) implements Configuration {}
    public record Thermometer(ThermometerType thermometerType) implements Configuration {}
    public record Microwave(Integer maxPowerWatts) implements Configuration {}
}
