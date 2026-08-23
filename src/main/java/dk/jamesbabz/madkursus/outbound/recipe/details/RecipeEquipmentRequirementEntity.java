package dk.jamesbabz.madkursus.outbound.recipe.details;
import java.util.UUID;import dk.jamesbabz.madkursus.service.models.EquipmentType;import jakarta.persistence.*;import lombok.*;
@Entity @Table(name="recipe_equipment_requirements") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class RecipeEquipmentRequirementEntity{@Id private UUID id;@ManyToOne(optional=false)@JoinColumn(name="recipe_id")private RecipeEntity recipe;@Enumerated(EnumType.STRING)private EquipmentType equipmentType;private String label;private int sortOrder;public RecipeEquipmentRequirementEntity(UUID id,EquipmentType type,String label,int sortOrder){this.id=id;equipmentType=type;this.label=label;this.sortOrder=sortOrder;}void setRecipe(RecipeEntity value){recipe=value;}}
