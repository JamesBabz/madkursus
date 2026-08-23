package dk.jamesbabz.madkursus.outbound.recipe.details;

import java.math.BigDecimal;import java.util.UUID;import jakarta.persistence.*;import lombok.*;
import dk.jamesbabz.madkursus.outbound.producttemplate.details.ProductTemplateEntity;
import dk.jamesbabz.madkursus.service.models.*;
@Entity @Table(name="recipe_process_bindings") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class RecipeProcessBindingEntity {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(optional=false) @JoinColumn(name="recipe_step_id") private RecipeStepEntity recipeStep;
 @Column(name="parameter_key",nullable=false) private String parameterKey;
 @ManyToOne @JoinColumn(name="recipe_ingredient_id") private RecipeIngredientEntity recipeIngredient;
 @ManyToOne @JoinColumn(name="product_template_id") private ProductTemplateEntity productTemplate;
 @ManyToOne @JoinColumn(name="prepared_component_id") private RecipePreparedComponentEntity preparedComponent;
 private BigDecimal quantity; @Enumerated(EnumType.STRING) private RecipeUnit unit;
 private Integer durationSeconds; private Integer temperatureCelsius; @Enumerated(EnumType.STRING) private HeatLevel heatLevel;
 @Column(name="number_value") private BigDecimal number; @Column(name="text_value") private String text;
 public RecipeProcessBindingEntity(UUID id,String key,RecipeIngredientEntity recipeIngredient,ProductTemplateEntity product, CookingProcessValue value){this.id=id;this.parameterKey=key;this.recipeIngredient=recipeIngredient;this.productTemplate=product;this.quantity=value.quantity();this.unit=value.unit();this.durationSeconds=value.durationSeconds();this.temperatureCelsius=value.temperatureCelsius();this.heatLevel=value.heatLevel();this.number=value.number();this.text=value.text();}
 public RecipeProcessBindingEntity(UUID id,String key,RecipeIngredientEntity recipeIngredient,ProductTemplateEntity product,CookingProcessValue value,RecipePreparedComponentEntity component){this(id,key,recipeIngredient,product,value);this.preparedComponent=component;}
 void setRecipeStep(RecipeStepEntity step){this.recipeStep=step;}
}
