package dk.jamesbabz.madkursus.outbound.recipetemplate.details;

import dk.jamesbabz.madkursus.service.models.RecipeUnit;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "recipe_template_prepared_component_ingredients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeTemplatePreparedComponentIngredientEntity {
    @Id private UUID id;
    @ManyToOne(optional = false) @JoinColumn(name = "prepared_component_id") private RecipeTemplatePreparedComponentEntity preparedComponent;
    @ManyToOne(optional = false) @JoinColumn(name = "recipe_ingredient_id") private RecipeTemplateIngredientEntity recipeIngredient;
    private BigDecimal quantity;
    @Enumerated(EnumType.STRING) private RecipeUnit unit;
    private int sortOrder;
}
