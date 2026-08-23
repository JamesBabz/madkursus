package dk.jamesbabz.madkursus.inbound.rest.cookingprocess;

import java.time.ZoneOffset;

import dk.jamesbabz.madkursus.inbound.rest.dto.*;
import dk.jamesbabz.madkursus.service.models.CookingProcess;
import dk.jamesbabz.madkursus.service.models.CookingProcessValue;
import org.springframework.stereotype.Component;

@Component
public class CookingProcessRestMapper {
    public CookingProcessDTO toDto(CookingProcess process) {
        return new CookingProcessDTO(process.id(), process.key(), process.name(), process.active(),
                process.createdAt().atOffset(ZoneOffset.UTC), process.updatedAt().atOffset(ZoneOffset.UTC),
                process.parameters().stream().map(parameter -> new CookingProcessParameterDTO(parameter.id(),
                        parameter.key(), parameter.label(), CookingProcessParameterTypeDTO.valueOf(parameter.type().name()),
                        parameter.required(), parameter.sortOrder()).defaultValue(value(parameter.defaultValue()))
                        .unit(parameter.unit() == null ? null : RecipeUnitDTO.valueOf(parameter.unit().name()))
                        .source(CookingProcessParameterSourceDTO.valueOf(parameter.source().name()))
                        .derivedRule(parameter.derivedRule()==null?null:parameter.derivedRule().name())
                        .derivedFrom(parameter.derivedFrom())).toList(),
                process.steps().stream().map(step -> new CookingProcessStepDTO(step.id(),
                        step.instructionTemplate(), step.sortOrder())).toList(),
                process.equipmentRequirements().stream().map(requirement ->
                        new CookingProcessEquipmentRequirementDTO(requirement.id(),
                                EquipmentTypeDTO.valueOf(requirement.equipmentType().name()),
                                EquipmentRequirementLevelDTO.valueOf(requirement.level().name()))).toList(),
                process.completionCriteriaTemplate()).description(process.description())
                .activeDurationSeconds(process.activeDurationSeconds()).passiveDurationSeconds(process.passiveDurationSeconds())
                .activeDurationParameterKey(process.activeDurationParameterKey()).passiveDurationParameterKey(process.passiveDurationParameterKey())
                .preparationRequirements(process.preparationRequirements().stream().map(value->new CookingProcessPreparationRequirementDTO(value.id(),value.parameterKey(),value.instructionTemplate(),value.sortOrder())).toList());
    }

    private CookingProcessValueDTO value(CookingProcessValue value) {
        if (value == null) return null;
        return new CookingProcessValueDTO().quantity(value.quantity())
                .unit(value.unit() == null ? null : RecipeUnitDTO.valueOf(value.unit().name()))
                .durationSeconds(value.durationSeconds()).temperatureCelsius(value.temperatureCelsius())
                .heatLevel(value.heatLevel() == null ? null : HeatLevelDTO.valueOf(value.heatLevel().name()))
                .number(value.number()).text(value.text());
    }
}
