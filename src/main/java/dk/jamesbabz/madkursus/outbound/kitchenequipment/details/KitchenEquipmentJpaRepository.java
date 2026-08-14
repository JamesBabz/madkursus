package dk.jamesbabz.madkursus.outbound.kitchenequipment.details;

import java.util.*;
import dk.jamesbabz.madkursus.service.models.EquipmentType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface KitchenEquipmentJpaRepository extends JpaRepository<KitchenEquipmentEntity,UUID> {
    Optional<KitchenEquipmentEntity> findByIdAndUserId(UUID id,UUID userId);
    List<KitchenEquipmentEntity> findAllByUserIdOrderByEquipmentTypeAscNameAsc(UUID userId);
    Optional<KitchenEquipmentEntity> findByUserIdAndEquipmentTypeAndPreferredTrueAndActiveTrue(UUID userId,EquipmentType type);
    @Query("select (count(e)>0) from KitchenEquipmentEntity e where e.userId=:user and e.equipmentType=:type and lower(e.name)=lower(:name) and (:excluding is null or e.id<>:excluding)")
    boolean duplicate(@Param("user")UUID user,@Param("type")EquipmentType type,@Param("name")String name,@Param("excluding")UUID excluding);
    @Modifying @Query("update KitchenEquipmentEntity e set e.preferred=false where e.userId=:user and e.equipmentType=:type and (:excluding is null or e.id<>:excluding)")
    void clearPreferred(@Param("user")UUID user,@Param("type")EquipmentType type,@Param("excluding")UUID excluding);
    long deleteByIdAndUserId(UUID id,UUID userId);
}
