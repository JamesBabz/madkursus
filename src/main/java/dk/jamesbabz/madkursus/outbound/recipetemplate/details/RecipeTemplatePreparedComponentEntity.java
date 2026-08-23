package dk.jamesbabz.madkursus.outbound.recipetemplate.details;

import jakarta.persistence.*;
import lombok.*;
import java.util.*;

@Entity
@Table(name = "recipe_template_prepared_components")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeTemplatePreparedComponentEntity {
    @Id private UUID id;
    @ManyToOne(optional = false) @JoinColumn(name = "recipe_template_id") private RecipeTemplateEntity recipeTemplate;
    @Column(name = "component_key") private String componentKey;
    private String name;
    private int sortOrder;
    @OneToMany(mappedBy = "preparedComponent") @OrderBy("sortOrder")
    private List<RecipeTemplatePreparedComponentIngredientEntity> ingredients = new ArrayList<>();
    @OneToMany(mappedBy = "preparedComponent") @OrderBy("sortOrder")
    private List<RecipeTemplatePreparationStepEntity> preparationSteps = new ArrayList<>();
}
