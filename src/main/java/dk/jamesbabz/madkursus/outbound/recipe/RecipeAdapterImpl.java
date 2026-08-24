package dk.jamesbabz.madkursus.outbound.recipe;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import dk.jamesbabz.madkursus.outbound.producttemplate.details.ProductTemplateEntity;
import dk.jamesbabz.madkursus.outbound.producttemplate.mappers.ProductTemplateEntityMapper;
import dk.jamesbabz.madkursus.outbound.recipe.details.*;
import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.ports.RecipePort;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RecipeAdapterImpl implements RecipePort {
    private final RecipeJpaRepository repository;
    private final ProductTemplateEntityMapper templateMapper;
    private final EntityManager entityManager;

    @Transactional public Recipe save(Recipe model){
        if(model.id()==null){RecipeEntity created=new RecipeEntity(null,model.userId(),model.sourceTemplateId(),model.name(),model.description(),model.createdAt(),model.updatedAt());addChildren(created,model);return map(repository.save(created));}
        RecipeEntity existing=repository.findByIdAndUserId(model.id(),model.userId()).orElseThrow(()->new dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException("Recipe",model.id()));
        existing.update(model.name(),model.description(),model.updatedAt()); existing.clearOwnedChildren(); repository.flush(); addChildren(existing,model); repository.flush(); return map(existing);
    }
    @Transactional(readOnly=true) public Optional<Recipe> findByIdAndUserId(UUID id,UUID userId){return repository.findByIdAndUserId(id,userId).map(this::map);}
    @Transactional(readOnly=true) public List<Recipe> findAllByUserId(UUID userId){return repository.findAllByUserIdOrderByUpdatedAtDesc(userId).stream().map(this::map).toList();}
    @Transactional(readOnly=true) public Optional<Recipe> findByUserIdAndSourceTemplateId(UUID userId,UUID sourceTemplateId){return repository.findByUserIdAndSourceTemplateId(userId,sourceTemplateId).map(this::map);}
    @Transactional public void deleteByIdAndUserId(UUID id,UUID userId){RecipeEntity entity=repository.findByIdAndUserId(id,userId).orElseThrow(()->new dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException("Recipe",id));entity.clearDependentChildren();repository.flush();entity.clearIngredients();repository.flush();repository.delete(entity);repository.flush();}

    private void addChildren(RecipeEntity entity,Recipe model) {
        Map<UUID,RecipeIngredientEntity> ingredients=model.ingredients().stream().map(value->{
            RecipeIngredientEntity ingredient=new RecipeIngredientEntity(value.id(),entityManager.getReference(ProductTemplateEntity.class,value.productTemplate().id()),value.quantity(),value.unit(),value.preparation(),value.sortOrder());
            entity.addIngredient(ingredient); return ingredient;
        }).collect(Collectors.toMap(RecipeIngredientEntity::getId,Function.identity()));
        Map<UUID,RecipePreparedComponentEntity> components=model.preparedComponents().stream().map(value->{RecipePreparedComponentEntity component=new RecipePreparedComponentEntity(value.id(),value.key(),value.name(),value.sortOrder());value.ingredients().forEach(allocation->component.addIngredient(new RecipePreparedComponentIngredientEntity(allocation.id(),ingredients.get(allocation.recipeIngredientId()),allocation.quantity(),allocation.unit(),allocation.sortOrder())));entity.addPreparedComponent(component);return component;}).collect(Collectors.toMap(RecipePreparedComponentEntity::getId,Function.identity()));
        model.steps().forEach(value->{
            RecipeStepEntity step=new RecipeStepEntity(null,value.type(),value.instruction(),value.structuredInstruction(),value.sortOrder(),value.cookingProcessId());
            value.parameterBindings().forEach(binding->step.addBinding(new RecipeProcessBindingEntity(null,binding.parameterKey(),
                    binding.recipeIngredientId()==null?null:ingredients.get(binding.recipeIngredientId()),
                    binding.productTemplate()==null?null:entityManager.getReference(ProductTemplateEntity.class,binding.productTemplate().id()),binding.value(),binding.preparedComponentId()==null?null:components.get(binding.preparedComponentId()))));
            entity.addStep(step);
        });
        model.preparationSteps().forEach(value->entity.addPreparationStep(new RecipePreparationStepEntity(value.id(),value.instruction(),value.structuredInstruction(),value.sortOrder())));
        model.equipmentRequirements().forEach(value->entity.addEquipmentRequirement(new RecipeEquipmentRequirementEntity(value.id(),value.equipmentType(),value.label(),value.sortOrder())));
    }
    private Recipe map(RecipeEntity entity){Map<UUID,PreparedComponent> components=entity.getPreparedComponents().stream().map(value->new PreparedComponent(value.getId(),value.getKey(),value.getName(),value.getSortOrder(),value.getIngredients().stream().map(a->new PreparedComponentIngredient(a.getId(),a.getRecipeIngredient().getId(),templateMapper.toModel(a.getRecipeIngredient().getProductTemplate()),a.getQuantity(),a.getUnit(),a.getSortOrder())).toList(),List.of())).collect(Collectors.toMap(PreparedComponent::id,Function.identity()));return new Recipe(entity.getId(),entity.getUserId(),entity.getSourceTemplateId(),entity.getName(),entity.getDescription(),entity.getCreatedAt(),entity.getUpdatedAt(),entity.getIngredients().stream().map(value->new RecipeIngredient(value.getId(),templateMapper.toModel(value.getProductTemplate()),value.getQuantity(),value.getUnit(),value.getPreparation(),value.getSortOrder())).toList(),entity.getSteps().stream().map(step->new RecipeStep(step.getId(),step.getStepType(),step.getInstruction(),step.getSortOrder(),step.getCookingProcessId(),step.getBindings().stream().map(binding->new CookingProcessBinding(binding.getId(),binding.getParameterKey(),binding.getRecipeIngredient()==null?null:binding.getRecipeIngredient().getId(),binding.getProductTemplate()==null?null:templateMapper.toModel(binding.getProductTemplate()),new CookingProcessValue(binding.getQuantity(),binding.getUnit(),binding.getDurationSeconds(),binding.getTemperatureCelsius(),binding.getHeatLevel(),binding.getNumber(),binding.getText()),binding.getPreparedComponent()==null?null:binding.getPreparedComponent().getId(),binding.getPreparedComponent()==null?null:components.get(binding.getPreparedComponent().getId()))).toList(),null,step.getStructuredInstruction())).toList(),entity.getPreparationSteps().stream().map(value->new RecipePreparationStep(value.getId(),value.getInstruction(),value.getSortOrder(),value.getStructuredInstruction())).toList(),entity.getEquipmentRequirements().stream().map(value->new RecipeEquipmentRequirement(value.getId(),value.getEquipmentType(),value.getLabel(),value.getSortOrder())).toList(),List.of(),List.copyOf(components.values()));}
}
