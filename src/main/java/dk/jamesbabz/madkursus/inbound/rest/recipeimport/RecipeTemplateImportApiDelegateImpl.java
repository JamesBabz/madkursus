package dk.jamesbabz.madkursus.inbound.rest.recipeimport;

import dk.jamesbabz.madkursus.inbound.rest.RecipeTemplateImportApiDelegate;
import dk.jamesbabz.madkursus.inbound.rest.dto.*;
import dk.jamesbabz.madkursus.tools.recipetemplate.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.List;
import java.io.IOException;

@Component
@lombok.extern.slf4j.Slf4j
public class RecipeTemplateImportApiDelegateImpl implements RecipeTemplateImportApiDelegate {
    private final boolean enabled;
    private final RecipeTemplateLocalImporter importer;
    public RecipeTemplateImportApiDelegateImpl(@Value("${madkursus.recipe-template-import.enabled:false}") boolean enabled,
            @Value("${madkursus.recipe-template-import.project-directory:.}") String project) {
        this.enabled=enabled;this.importer=new RecipeTemplateLocalImporter(Path.of(project));
    }
    @Override public ResponseEntity<RecipeTemplateImportStatusDTO> getRecipeTemplateImportStatus() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new RecipeTemplateImportStatusDTO().enabled(enabled));
    }
    @Override public ResponseEntity<RecipeTemplateImportResultDTO> validateRecipeTemplateImport(String body) {return process(body,false);}
    @Override public ResponseEntity<RecipeTemplateImportResultDTO> importRecipeTemplateDraft(String body) {return process(body,true);}
    private ResponseEntity<RecipeTemplateImportResultDTO> process(String body,boolean write) {
        if(!enabled)return ResponseEntity.status(403).cacheControl(CacheControl.noStore()).build();
        var dto=new RecipeTemplateImportResultDTO().valid(false).imported(false).errors(List.of()).warnings(List.of());
        try {
            if(body==null||body.isBlank()||body.length()>500000)throw new IllegalArgumentException("JSON must contain 1–500000 characters");
            RecipeTemplateLocalImporter.Outcome outcome=write?importer.importDraft(body):null;
            var prepared=write?outcome.draft():importer.validate(body);var result=prepared.validation();
            dto.valid(true).imported(write).key(result.key()).name(result.name())
                    .action(result.update()?RecipeTemplateImportResultDTO.ActionEnum.UPDATE:RecipeTemplateImportResultDTO.ActionEnum.ADD)
                    .ingredients(result.ingredients()).preparationSteps(result.preparation()).preparedComponents(result.components())
                    .cookingProcesses(result.processes().size()).textSteps(prepared.textSteps()).processSteps(prepared.processSteps()).warnings(prepared.warnings());
            if(write)dto.canonicalFile("recipe-templates.json").migrationFile(outcome.migration());
        } catch(com.fasterxml.jackson.core.JsonProcessingException failure) {
            var error=new RecipeTemplateImportErrorDTO().path("$");
            var parserLocation=failure.getLocation();
            if(parserLocation!=null) {
                error.line(parserLocation.getLineNr()).column(parserLocation.getColumnNr());
                error.message("Invalid JSON at line "+parserLocation.getLineNr()+", column "+parserLocation.getColumnNr()+": "+failure.getOriginalMessage());
            } else error.message("Invalid JSON: "+failure.getOriginalMessage());
            dto.errors(List.of(error));
        } catch(RecipeTemplateDraftTool.ValidationException failure) {
            dto.errors(failure.errors().stream().map(error->new RecipeTemplateImportErrorDTO().path(error.path()).message(error.message())).toList());
        } catch(IllegalArgumentException failure) {
            dto.errors(List.of(new RecipeTemplateImportErrorDTO().path("$").message(failure.getMessage())));
        } catch(IOException failure) {
            log.warn("Local recipe authoring failed operation={}",write?"import":"validate",failure);
            dto.errors(List.of(new RecipeTemplateImportErrorDTO().path("$").message("Kunne ikke klargøre eller skrive de lokale kildefiler. Kontrollér projektmappe, migrationsversioner og skriverettigheder. Se serverloggen.")));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dto);
    }
}
