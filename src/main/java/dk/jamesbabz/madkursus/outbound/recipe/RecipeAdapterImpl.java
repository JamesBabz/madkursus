package dk.jamesbabz.madkursus.outbound.recipe;
import java.util.*; import dk.jamesbabz.madkursus.outbound.producttemplate.details.ProductTemplateEntity; import dk.jamesbabz.madkursus.outbound.producttemplate.mappers.ProductTemplateEntityMapper; import dk.jamesbabz.madkursus.outbound.recipe.details.*; import dk.jamesbabz.madkursus.service.models.*; import dk.jamesbabz.madkursus.service.ports.RecipePort; import jakarta.persistence.EntityManager; import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Component; import org.springframework.transaction.annotation.Transactional;
@Component @RequiredArgsConstructor
public class RecipeAdapterImpl implements RecipePort {
 private final RecipeJpaRepository repository; private final ProductTemplateEntityMapper templateMapper; private final EntityManager entityManager;
 @Transactional public Recipe save(Recipe model){
  if(model.id()==null){RecipeEntity created=new RecipeEntity(null,model.userId(),model.sourceTemplateId(),model.name(),model.description(),model.createdAt(),model.updatedAt());addChildren(created,model);return map(repository.save(created));}
  RecipeEntity existing=repository.findByIdAndUserId(model.id(),model.userId()).orElseThrow(()->new dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException("Recipe",model.id()));
  existing.update(model.name(),model.description(),model.updatedAt());
  existing.clearOwnedChildren();
  repository.flush();
  addChildren(existing,model);
  repository.flush();
  return map(existing);
 }
 @Transactional(readOnly=true) public Optional<Recipe> findByIdAndUserId(UUID id,UUID userId){return repository.findByIdAndUserId(id,userId).map(this::map);} @Transactional(readOnly=true) public List<Recipe> findAllByUserId(UUID userId){return repository.findAllByUserIdOrderByUpdatedAtDesc(userId).stream().map(this::map).toList();}
 @Transactional(readOnly=true) public Optional<Recipe> findByUserIdAndSourceTemplateId(UUID userId,UUID sourceTemplateId){return repository.findByUserIdAndSourceTemplateId(userId,sourceTemplateId).map(this::map);}
 @Transactional public void deleteByIdAndUserId(UUID id,UUID userId){repository.deleteByIdAndUserId(id,userId);}
 private void addChildren(RecipeEntity entity,Recipe model){model.ingredients().forEach(i->entity.addIngredient(new RecipeIngredientEntity(null,entityManager.getReference(ProductTemplateEntity.class,i.productTemplate().id()),i.quantity(),i.unit(),i.preparation(),i.sortOrder())));model.steps().forEach(s->entity.addStep(new RecipeStepEntity(null,s.instruction(),s.sortOrder())));}
 private Recipe map(RecipeEntity e){return new Recipe(e.getId(),e.getUserId(),e.getSourceTemplateId(),e.getName(),e.getDescription(),e.getCreatedAt(),e.getUpdatedAt(),e.getIngredients().stream().map(i->new RecipeIngredient(i.getId(),templateMapper.toModel(i.getProductTemplate()),i.getQuantity(),i.getUnit(),i.getPreparation(),i.getSortOrder())).toList(),e.getSteps().stream().map(s->new RecipeStep(s.getId(),s.getInstruction(),s.getSortOrder())).toList());}
}
