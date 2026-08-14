package dk.jamesbabz.madkursus.outbound.kitchenequipment.details;

import java.time.Instant;
import java.util.*;
import dk.jamesbabz.madkursus.service.models.EquipmentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="kitchen_equipment") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class KitchenEquipmentEntity {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(nullable=false) private UUID userId;
    @Enumerated(EnumType.STRING) private EquipmentType equipmentType;
    private String name; private String normalizedName; private boolean active; private boolean preferred;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") private Map<String,Object> configuration;
    private Instant createdAt; private Instant updatedAt;
    public KitchenEquipmentEntity(UUID id,UUID userId,EquipmentType equipmentType,String name,String normalizedName,
            boolean active,boolean preferred,Map<String,Object> configuration,Instant createdAt,Instant updatedAt) {
        this.id=id;this.userId=userId;this.equipmentType=equipmentType;this.name=name;this.normalizedName=normalizedName;
        this.active=active;this.preferred=preferred;this.configuration=configuration;this.createdAt=createdAt;this.updatedAt=updatedAt;
    }
}
