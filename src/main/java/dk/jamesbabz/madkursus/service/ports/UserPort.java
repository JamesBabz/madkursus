package dk.jamesbabz.madkursus.service.ports;

import java.util.Optional;

import dk.jamesbabz.madkursus.service.models.User;

public interface UserPort {
    User save(User user);
    Optional<User> findByUsername(String normalizedUsername);
    boolean existsByUsername(String normalizedUsername);
}
