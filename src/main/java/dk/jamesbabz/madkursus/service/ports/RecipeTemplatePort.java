package dk.jamesbabz.madkursus.service.ports;
import java.util.*; import dk.jamesbabz.madkursus.service.models.RecipeTemplate;
public interface RecipeTemplatePort {List<RecipeTemplate> search(String query);Optional<RecipeTemplate> findById(UUID id);}
