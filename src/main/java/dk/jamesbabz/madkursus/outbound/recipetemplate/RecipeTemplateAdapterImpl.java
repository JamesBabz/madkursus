package dk.jamesbabz.madkursus.outbound.recipetemplate;

import dk.jamesbabz.madkursus.outbound.producttemplate.mappers.ProductTemplateEntityMapper;
import dk.jamesbabz.madkursus.outbound.recipetemplate.details.*;
import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.ports.RecipeTemplatePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component @RequiredArgsConstructor
public class RecipeTemplateAdapterImpl implements RecipeTemplatePort {
    private final RecipeTemplateJpaRepository repository;
    private final ProductTemplateEntityMapper products;

    @Transactional(readOnly=true) public List<RecipeTemplate> search(String query) {
        return repository.search(query==null?"":query.trim()).stream().map(this::map).toList();
    }
    @Transactional(readOnly=true) public Optional<RecipeTemplate> findById(UUID id) {
        return repository.findByIdAndActiveTrue(id).map(this::map);
    }
    private RecipeTemplate map(RecipeTemplateEntity entity) {
        Map<UUID,PreparedComponent> components=entity.getPreparedComponents().stream().map(this::component)
                .collect(Collectors.toMap(PreparedComponent::id, Function.identity()));
        return new RecipeTemplate(entity.getId(),entity.getName(),entity.getDescription(),entity.isActive(),entity.getCreatedAt(),entity.getUpdatedAt(),
                entity.getIngredients().stream().map(i->new RecipeTemplateIngredient(i.getId(),products.toModel(i.getProductTemplate()),i.getQuantity(),i.getUnit(),i.getPreparation(),i.getSortOrder())).toList(),
                entity.getSteps().stream().map(s->new RecipeTemplateStep(s.getId(),s.getStepType(),s.getInstruction(),s.getSortOrder(),s.getCookingProcessId(),s.getBindings().stream().map(b->binding(b,components)).toList(),null)).toList(),
                entity.getPreparationSteps().stream().filter(p->p.getPreparedComponent()==null).map(p->new RecipePreparationStep(p.getId(),p.getInstruction(),p.getSortOrder())).toList(),
                entity.getEquipmentRequirements().stream().map(e->new RecipeEquipmentRequirement(e.getId(),e.getEquipmentType(),e.getLabel(),e.getSortOrder())).toList(),List.of(),
                components.values().stream().sorted(Comparator.comparingInt(PreparedComponent::sortOrder)).toList());
    }
    private PreparedComponent component(RecipeTemplatePreparedComponentEntity value) {
        return new PreparedComponent(value.getId(),value.getComponentKey(),value.getName(),value.getSortOrder(),
                value.getIngredients().stream().map(a->new PreparedComponentIngredient(a.getId(),a.getRecipeIngredient().getId(),products.toModel(a.getRecipeIngredient().getProductTemplate()),a.getQuantity(),a.getUnit(),a.getSortOrder())).toList(),
                value.getPreparationSteps().stream().map(p->new RecipePreparationStep(p.getId(),p.getInstruction(),p.getSortOrder())).toList());
    }
    private CookingProcessBinding binding(RecipeTemplateProcessBindingEntity value,Map<UUID,PreparedComponent> components) {
        PreparedComponent component=value.getPreparedComponent()==null?null:components.get(value.getPreparedComponent().getId());
        return new CookingProcessBinding(value.getId(),value.getParameterKey(),value.getRecipeIngredientId(),value.getProductTemplate()==null?null:products.toModel(value.getProductTemplate()),
                new CookingProcessValue(value.getQuantity(),value.getUnit(),value.getDurationSeconds(),value.getTemperatureCelsius(),value.getHeatLevel(),value.getNumber(),value.getText()),
                component==null?null:component.id(),component);
    }
}
