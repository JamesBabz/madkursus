package dk.jamesbabz.madkursus.inbound.security;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import dk.jamesbabz.madkursus.service.models.User;
import dk.jamesbabz.madkursus.service.ports.UserPort;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticationTest {
    @Test
    void validPasswordAuthenticatesAndInvalidPasswordDoesNot() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        UserPort port = mock(UserPort.class);
        when(port.findByUsername("user")).thenReturn(Optional.of(
                new User(UUID.randomUUID(), "user", encoder.encode("correct-password"), Instant.now(), true)));
        DaoAuthenticationProvider provider = provider(port, encoder);
        assertThat(provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("USER", "correct-password"))
                .isAuthenticated()).isTrue();
        assertThatThrownBy(() -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("user", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void disabledUserCannotAuthenticate() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        UserPort port = mock(UserPort.class);
        when(port.findByUsername("disabled")).thenReturn(Optional.of(
                new User(UUID.randomUUID(), "disabled", encoder.encode("correct-password"), Instant.now(), false)));
        assertThatThrownBy(() -> provider(port, encoder).authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("disabled", "correct-password")))
                .isInstanceOf(DisabledException.class);
    }

    private DaoAuthenticationProvider provider(UserPort port, BCryptPasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(new DatabaseUserDetailsService(port));
        provider.setPasswordEncoder(encoder);
        return provider;
    }
}
