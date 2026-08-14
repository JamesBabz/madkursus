package dk.jamesbabz.madkursus.service.ports;

import java.util.*;
import dk.jamesbabz.madkursus.service.models.*;

public interface KitchenEquipmentPort {
    KitchenEquipment save(KitchenEquipment equipment);
    Optional<KitchenEquipment> findByIdAndUserId(UUID id, UUID userId);
    List<KitchenEquipment> findAllByUserId(UUID userId);
    Optional<KitchenEquipment> findPreferredByUserIdAndType(UUID userId, EquipmentType type);
    boolean existsByUserIdAndTypeAndName(UUID userId, EquipmentType type, String name, UUID excludingId);
    void clearPreferred(UUID userId, EquipmentType type, UUID excludingId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
