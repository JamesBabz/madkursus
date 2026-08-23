package dk.jamesbabz.madkursus.outbound.recipe.details;
import java.time.Instant; import java.util.*; import jakarta.persistence.*; import lombok.Getter; import lombok.NoArgsConstructor;
@Entity @Table(name="recipes") @Getter @NoArgsConstructor(access=lombok.AccessLevel.PROTECTED)
public class RecipeEntity {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(nullable=false) private UUID userId; @Column(name="source_template_id") private UUID sourceTemplateId; private String name; private String description; private Instant createdAt; private Instant updatedAt;
 @OneToMany(mappedBy="recipe",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("sortOrder") private List<RecipeIngredientEntity> ingredients=new ArrayList<>();
 @OneToMany(mappedBy="recipe",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("sortOrder") private List<RecipeStepEntity> steps=new ArrayList<>();
 @OneToMany(mappedBy="recipe",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("sortOrder") private List<RecipePreparationStepEntity> preparationSteps=new ArrayList<>();
 @OneToMany(mappedBy="recipe",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("sortOrder") private List<RecipeEquipmentRequirementEntity> equipmentRequirements=new ArrayList<>();
 @OneToMany(mappedBy="recipe",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("sortOrder") private List<RecipePreparedComponentEntity> preparedComponents=new ArrayList<>();
 public RecipeEntity(UUID id,UUID userId,UUID sourceTemplateId,String name,String description,Instant createdAt,Instant updatedAt){this.id=id;this.userId=userId;this.sourceTemplateId=sourceTemplateId;this.name=name;this.description=description;this.createdAt=createdAt;this.updatedAt=updatedAt;}
 public RecipeEntity(UUID id,UUID userId,String name,String description,Instant createdAt,Instant updatedAt){this(id,userId,null,name,description,createdAt,updatedAt);}
 public void update(String name,String description,Instant updatedAt){this.name=name;this.description=description;this.updatedAt=updatedAt;}
 public void clearOwnedChildren(){steps.clear();preparationSteps.clear();equipmentRequirements.clear();preparedComponents.clear();ingredients.clear();}
 public void addIngredient(RecipeIngredientEntity value){ingredients.add(value);value.setRecipe(this);} public void addStep(RecipeStepEntity value){steps.add(value);value.setRecipe(this);}public void addPreparationStep(RecipePreparationStepEntity value){preparationSteps.add(value);value.setRecipe(this);}public void addEquipmentRequirement(RecipeEquipmentRequirementEntity value){equipmentRequirements.add(value);value.setRecipe(this);}
 public void addPreparedComponent(RecipePreparedComponentEntity value){preparedComponents.add(value);value.setRecipe(this);}
}
