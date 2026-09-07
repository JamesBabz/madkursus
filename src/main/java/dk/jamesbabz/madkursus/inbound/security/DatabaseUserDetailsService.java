package dk.jamesbabz.madkursus.inbound.security;

import dk.jamesbabz.madkursus.service.applications.UserService;
import dk.jamesbabz.madkursus.service.models.User;
import dk.jamesbabz.madkursus.service.ports.UserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
@RequiredArgsConstructor
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserPort userPort;
    @Value("${madkursus.admin-username:}") private String adminUsername;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userPort.findByUsername(UserService.normalizeUsername(username))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return new AuthenticatedUser(user.id(), user.username(), user.passwordHash(), user.enabled(),adminUsername!=null&&!adminUsername.isBlank()&&user.username().equals(UserService.normalizeUsername(adminUsername)));
    }
}
