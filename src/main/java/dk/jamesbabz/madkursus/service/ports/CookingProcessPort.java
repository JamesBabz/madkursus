package dk.jamesbabz.madkursus.service.ports;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import dk.jamesbabz.madkursus.service.models.CookingProcess;

public interface CookingProcessPort {
    List<CookingProcess> search(String query);
    Optional<CookingProcess> findById(UUID id);
}
