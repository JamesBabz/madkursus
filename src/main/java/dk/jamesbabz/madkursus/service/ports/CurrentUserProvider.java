package dk.jamesbabz.madkursus.service.ports;

import java.util.UUID;

public interface CurrentUserProvider {
    UUID currentUserId();
}
