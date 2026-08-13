package dk.jamesbabz.madkursus.inbound.rest.auth;

import java.util.UUID;

import dk.jamesbabz.madkursus.inbound.rest.dto.LoginRequestDTO;
import dk.jamesbabz.madkursus.inbound.security.AuthenticatedUser;
import dk.jamesbabz.madkursus.service.applications.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthApiDelegateImplTest {
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void loginEstablishesSessionAndSavesSecurityContext() {
        UUID id = UUID.randomUUID();
        AuthenticatedUser principal = new AuthenticatedUser(id, "user", "hash", true);
        Authentication authenticated = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        AuthenticationManager manager = mock(AuthenticationManager.class);
        when(manager.authenticate(any())).thenReturn(authenticated);
        SecurityContextRepository repository = mock(SecurityContextRepository.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthApiDelegateImpl delegate = new AuthApiDelegateImpl(mock(UserService.class), manager, repository, request, response);

        var result = delegate.login(new LoginRequestDTO("USER", "correct-password"));

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().getId()).isEqualTo(id);
        assertThat(request.getSession(false)).isNotNull();
        verify(repository).saveContext(any(), any(), any());
    }

    @Test
    void logoutInvalidatesSessionAndClearsAuthentication() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("user", null, java.util.List.of()));
        AuthApiDelegateImpl delegate = new AuthApiDelegateImpl(mock(UserService.class), mock(AuthenticationManager.class),
                mock(SecurityContextRepository.class), request, response);

        assertThat(delegate.logout().getStatusCode().value()).isEqualTo(204);
        assertThat(request.getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
