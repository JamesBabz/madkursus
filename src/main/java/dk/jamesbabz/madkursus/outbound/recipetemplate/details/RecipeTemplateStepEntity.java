package dk.jamesbabz.madkursus.outbound.recipetemplate.details;
import java.util.UUID;import jakarta.persistence.*;import lombok.*;
@Entity @Table(name="recipe_template_steps") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED) public class RecipeTemplateStepEntity{@Id private UUID id;@ManyToOne(optional=false)@JoinColumn(name="recipe_template_id")private RecipeTemplateEntity recipeTemplate;private String instruction;private int sortOrder;}
