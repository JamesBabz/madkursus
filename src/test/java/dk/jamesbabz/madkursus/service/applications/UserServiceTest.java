package dk.jamesbabz.madkursus.service.applications;

import dk.jamesbabz.madkursus.service.exceptions.DuplicateUsernameException;
import dk.jamesbabz.madkursus.service.exceptions.RegistrationDisabledException;
import dk.jamesbabz.madkursus.service.models.User;
import dk.jamesbabz.madkursus.service.ports.UserPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserPort userPort;
    private UserService service;

    @BeforeEach
    void setUp() { service = new UserService(userPort, new BCryptPasswordEncoder()); }

    @Test
    void registrationHashesPasswordAndNormalizesUsername() {
        ReflectionTestUtils.setField(service, "registrationEnabled", true);
        when(userPort.save(any())).thenAnswer(call -> call.getArgument(0));
        User user = service.register(" JamesBabz ", "long-password");
        assertThat(user.username()).isEqualTo("jamesbabz");
        assertThat(user.passwordHash()).isNotEqualTo("long-password");
        assertThat(new BCryptPasswordEncoder().matches("long-password", user.passwordHash())).isTrue();
    }

    @Test
    void registrationIsRejectedWhenDisabled() {
        ReflectionTestUtils.setField(service, "registrationEnabled", false);
        assertThatThrownBy(() -> service.register("user", "long-password"))
                .isInstanceOf(RegistrationDisabledException.class);
    }

    @Test
    void duplicateUsernameIsRejected() {
        ReflectionTestUtils.setField(service, "registrationEnabled", true);
        when(userPort.existsByUsername("user")).thenReturn(true);
        assertThatThrownBy(() -> service.register("USER", "long-password"))
                .isInstanceOf(DuplicateUsernameException.class);
    }
}
