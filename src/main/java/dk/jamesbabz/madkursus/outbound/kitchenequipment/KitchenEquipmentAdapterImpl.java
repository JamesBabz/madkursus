package dk.jamesbabz.madkursus.outbound.kitchenequipment;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import dk.jamesbabz.madkursus.outbound.kitchenequipment.details.*;
import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.models.KitchenEquipment.*;
import dk.jamesbabz.madkursus.service.ports.KitchenEquipmentPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @RequiredArgsConstructor
public class KitchenEquipmentAdapterImpl implements KitchenEquipmentPort {
    private final KitchenEquipmentJpaRepository repository; private final ObjectMapper objectMapper;
    public KitchenEquipment save(KitchenEquipment value) { return map(repository.save(entity(value))); }
    public Optional<KitchenEquipment> findByIdAndUserId(UUID id,UUID userId){return repository.findByIdAndUserId(id,userId).map(this::map);}
    public List<KitchenEquipment> findAllByUserId(UUID userId){return repository.findAllByUserIdOrderByEquipmentTypeAscNameAsc(userId).stream().map(this::map).toList();}
    public Optional<KitchenEquipment> findPreferredByUserIdAndType(UUID userId,EquipmentType type){return repository.findByUserIdAndEquipmentTypeAndPreferredTrueAndActiveTrue(userId,type).map(this::map);}
    public boolean existsByUserIdAndTypeAndName(UUID userId,EquipmentType type,String name,UUID excludingId){return repository.duplicate(userId,type,name,excludingId);}
    @Transactional public void clearPreferred(UUID userId,EquipmentType type,UUID excludingId){repository.clearPreferred(userId,type,excludingId);}
    @Transactional public void deleteByIdAndUserId(UUID id,UUID userId){repository.deleteByIdAndUserId(id,userId);}
    private KitchenEquipmentEntity entity(KitchenEquipment e){return new KitchenEquipmentEntity(e.id(),e.userId(),e.equipmentType(),e.name(),e.name().trim().toLowerCase(Locale.ROOT),e.active(),e.preferred(),objectMapper.convertValue(e.configuration(),Map.class),e.createdAt(),e.updatedAt());}
    private KitchenEquipment map(KitchenEquipmentEntity e){return new KitchenEquipment(e.getId(),e.getUserId(),e.getEquipmentType(),e.getName(),e.isActive(),e.isPreferred(),configuration(e),e.getCreatedAt(),e.getUpdatedAt());}
    private Configuration configuration(KitchenEquipmentEntity e){Class<? extends Configuration> type=switch(e.getEquipmentType()){case STOVE->Stove.class;case OVEN->Oven.class;case POT->Pot.class;case PAN->Pan.class;case AIR_FRYER->AirFryer.class;case THERMOMETER->Thermometer.class;case MICROWAVE->Microwave.class;};return objectMapper.convertValue(e.getConfiguration(),type);}
}
