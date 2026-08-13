package dk.jamesbabz.madkursus.service.applications;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import dk.jamesbabz.madkursus.service.exceptions.DuplicateUsernameException;
import dk.jamesbabz.madkursus.service.exceptions.RegistrationDisabledException;
import dk.jamesbabz.madkursus.service.exceptions.InvalidInputException;
import dk.jamesbabz.madkursus.service.models.User;
import dk.jamesbabz.madkursus.service.ports.UserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserPort userPort;
    private final PasswordEncoder passwordEncoder;

    @Value("${spring.security.registration-enabled:false}")
    private boolean registrationEnabled;

    public User register(String username, String password) {
        if (!registrationEnabled) throw new RegistrationDisabledException();
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername.isBlank()) throw new InvalidInputException("Username is required");
        if (userPort.existsByUsername(normalizedUsername)) throw new DuplicateUsernameException();
        return userPort.save(new User(UUID.randomUUID(), normalizedUsername, passwordEncoder.encode(password),
                Instant.now(), true));
    }

    public boolean isRegistrationEnabled() { return registrationEnabled; }

    public User findByUsername(String username) {
        return userPort.findByUsername(normalizeUsername(username)).orElseThrow();
    }

    public static String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
