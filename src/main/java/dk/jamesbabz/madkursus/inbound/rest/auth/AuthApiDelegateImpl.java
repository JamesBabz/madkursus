package dk.jamesbabz.madkursus.inbound.rest.auth;

import java.net.URI;

import dk.jamesbabz.madkursus.inbound.rest.AuthApiDelegate;
import dk.jamesbabz.madkursus.inbound.rest.dto.CsrfTokenDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.CurrentUserDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.LoginRequestDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.RegisterRequestDTO;
import dk.jamesbabz.madkursus.inbound.rest.dto.RegistrationStatusDTO;
import dk.jamesbabz.madkursus.inbound.security.AuthenticatedUser;
import dk.jamesbabz.madkursus.service.applications.UserService;
import dk.jamesbabz.madkursus.service.models.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthApiDelegateImpl implements AuthApiDelegate {
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final HttpServletRequest request;
    private final HttpServletResponse response;

    @Override
    public ResponseEntity<CurrentUserDTO> login(LoginRequestDTO credentials) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(credentials.getUsername(), credentials.getPassword()));
        request.getSession(true);
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return ResponseEntity.ok(toDto((AuthenticatedUser) authentication.getPrincipal()));
    }

    @Override
    public ResponseEntity<Void> logout() {
        new SecurityContextLogoutHandler().logout(request, response,
                SecurityContextHolder.getContext().getAuthentication());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<CurrentUserDTO> getCurrentUser() {
        return ResponseEntity.ok(toDto((AuthenticatedUser) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal()));
    }

    @Override
    public ResponseEntity<CurrentUserDTO> register(RegisterRequestDTO registration) {
        User user = userService.register(registration.getUsername(), registration.getPassword());
        return ResponseEntity.created(URI.create("/v1/auth/me"))
                .body(new CurrentUserDTO(user.id(), user.username()));
    }

    @Override
    public ResponseEntity<RegistrationStatusDTO> getRegistrationStatus() {
        return ResponseEntity.ok(new RegistrationStatusDTO(userService.isRegistrationEnabled()));
    }

    @Override
    public ResponseEntity<CsrfTokenDTO> getCsrfToken() {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return ResponseEntity.ok(new CsrfTokenDTO(token.getToken()));
    }

    private CurrentUserDTO toDto(AuthenticatedUser user) {
        return new CurrentUserDTO(user.id(), user.username());
    }
}
