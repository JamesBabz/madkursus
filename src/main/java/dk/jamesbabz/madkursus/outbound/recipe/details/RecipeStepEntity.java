package dk.jamesbabz.madkursus.outbound.recipe.details;
import java.util.UUID; import jakarta.persistence.*; import lombok.Getter; import lombok.NoArgsConstructor;
@Entity @Table(name="recipe_steps") @Getter @NoArgsConstructor(access=lombok.AccessLevel.PROTECTED)
public class RecipeStepEntity { @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id; @ManyToOne(optional=false) @JoinColumn(name="recipe_id") private RecipeEntity recipe; private String instruction; private int sortOrder; public RecipeStepEntity(UUID id,String instruction,int sortOrder){this.id=id;this.instruction=instruction;this.sortOrder=sortOrder;} void setRecipe(RecipeEntity recipe){this.recipe=recipe;}}
