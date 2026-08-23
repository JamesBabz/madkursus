package dk.jamesbabz.madkursus.outbound.recipetemplate.details;
import java.util.UUID;import dk.jamesbabz.madkursus.service.models.EquipmentType;import jakarta.persistence.*;import lombok.*;
@Entity @Table(name="recipe_template_equipment_requirements") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class RecipeTemplateEquipmentRequirementEntity{@Id private UUID id;@ManyToOne(optional=false)@JoinColumn(name="recipe_template_id")private RecipeTemplateEntity recipeTemplate;@Enumerated(EnumType.STRING)private EquipmentType equipmentType;private String label;private int sortOrder;}
