package dk.jamesbabz.madkursus.inbound.rest.nutritionadmin;

import dk.jamesbabz.madkursus.inbound.rest.NutritionAdminApiDelegate;
import dk.jamesbabz.madkursus.inbound.rest.dto.*;
import dk.jamesbabz.madkursus.service.applications.DtuNutritionAdminService;
import dk.jamesbabz.madkursus.service.applications.NutritionAdminService;
import dk.jamesbabz.madkursus.service.models.NutritionData;
import dk.jamesbabz.madkursus.service.models.RecipeUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class NutritionAdminApiDelegateImpl implements NutritionAdminApiDelegate {
    private final NutritionAdminService service;
    private final DtuNutritionAdminService dtu;

    @Override public ResponseEntity<List<ProductTemplateNutritionDTO>> getProductTemplateNutrition(String status,String search){return ResponseEntity.ok(service.list(status,search).stream().map(this::dto).toList());}
    @Override public ResponseEntity<List<RecipeTemplateNutritionCoverageDTO>> getRecipeTemplateNutritionCoverage(){return ResponseEntity.ok(service.coverage().stream().map(this::coverage).toList());}
    @Override public ResponseEntity<ProductTemplateNutritionDTO> updateProductTemplateNutrition(UUID id,NutritionDataInputDTO input){NutritionData data=new NutritionData(input.getCarbohydrateGrams(),input.getBasisQuantity(),RecipeUnit.valueOf(input.getBasisUnit().name()),input.getSource(),input.getProvider(),input.getExternalFoodId(),input.getSourceVersion(),input.getSourceUrl(),input.getNote());return ResponseEntity.ok(dto(service.update(id,data)));}
    @Override public ResponseEntity<DtuImportResultDTO> importBundledDtuCatalog(){var result=dtu.importBundled();return ResponseEntity.ok(new DtuImportResultDTO(result.datasetVersion(),result.importedFoods(),result.alreadyImported()));}
    @Override public ResponseEntity<List<DtuReferenceFoodDTO>> searchDtuCatalog(String search){return ResponseEntity.ok(dtu.search(search).stream().map(this::food).toList());}
    @Override public ResponseEntity<ProductTemplateNutritionDTO> approveDtuMapping(UUID id,DtuMappingApprovalDTO approval){return ResponseEntity.ok(dto(dtu.approve(new DtuNutritionAdminService.Approval(id,approval.getDatasetVersion(),approval.getFoodId()),service)));}
    @Override public ResponseEntity<List<ProductTemplateNutritionDTO>> approveDtuMappings(List<DtuBulkMappingApprovalDTO> approvals){var values=approvals.stream().map(value->new DtuNutritionAdminService.Approval(value.getProductTemplateId(),value.getDatasetVersion(),value.getFoodId())).toList();return ResponseEntity.ok(dtu.approveAll(values,service).stream().map(this::dto).toList());}
    @Override public ResponseEntity<List<ProductTemplateNutritionDTO>> approveSafeDtuMappings(){return ResponseEntity.ok(dtu.approveSafe(service).stream().map(this::dto).toList());}

    private ProductTemplateNutritionDTO dto(NutritionAdminService.Entry entry){NutritionData nutrition=entry.template().nutritionData();NutritionDataInputDTO value=nutrition==null?null:new NutritionDataInputDTO(nutrition.carbohydrateGrams(),nutrition.basisQuantity(),RecipeUnitDTO.valueOf(nutrition.basisUnit().name()),nutrition.source()).provider(nutrition.provider()).externalFoodId(nutrition.externalFoodId()).sourceVersion(nutrition.sourceVersion()).sourceUrl(nutrition.sourceUrl()).note(nutrition.note());var suggestion=dtu.suggest(entry.template());var mapping=dtu.mapping(entry.template().id());return new ProductTemplateNutritionDTO(entry.template().id(),entry.template().name(),entry.key(),entry.template().aliases(),ProductTemplateNutritionDTO.StatusEnum.valueOf(entry.status().name())).nutrition(value).dtuSuggestion(suggestion(suggestion)).dtuMapping(mapping(mapping)).recipeTemplateUsageCount(entry.recipeTemplateUsageCount());}
    private RecipeTemplateNutritionCoverageDTO coverage(NutritionAdminService.RecipeCoverage value){return new RecipeTemplateNutritionCoverageDTO(value.recipeTemplateId(),value.name(),value.coveredIngredients(),value.totalIngredients(),value.missing().stream().map(this::dto).toList());}
    private DtuSuggestionDTO suggestion(DtuNutritionAdminService.Suggestion value){return new DtuSuggestionDTO(DtuSuggestionDTO.ClassificationEnum.valueOf(value.classification().name()),value.candidates().stream().map(candidate->new DtuCandidateDTO(food(candidate.food()),DtuCandidateDTO.ClassificationEnum.valueOf(candidate.classification().name()),candidate.score(),candidate.reason())).toList()).reason(value.reason()).resolution(DtuSuggestionDTO.ResolutionEnum.valueOf(value.resolution().name())).representative(value.representative()==null?null:food(value.representative())).equivalentCandidateCount(value.equivalentCandidateCount()).carbohydrateSpread(value.carbohydrateSpread());}
    private DtuMappingDTO mapping(DtuNutritionAdminService.Mapping value){return new DtuMappingDTO(DtuMappingDTO.StatusEnum.valueOf(value.status().name())).food(value.food()==null?null:food(value.food())).approvedCarbohydrateGrams(value.approvedCarbohydrateGrams());}
    private DtuReferenceFoodDTO food(DtuNutritionAdminService.Food value){return new DtuReferenceFoodDTO(value.datasetVersion(),value.foodId(),value.danishName(),value.carbohydrateGrams(),value.sourceDate(),value.sourceUrl()).stateDescription(value.stateDescription());}
}
