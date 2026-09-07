package dk.jamesbabz.madkursus.inbound.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record AuthenticatedUser(UUID id, String username, String password, boolean enabled,boolean admin) implements UserDetails {
    public AuthenticatedUser(UUID id,String username,String password,boolean enabled){this(id,username,password,enabled,false);}
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return admin?List.of(new SimpleGrantedAuthority("ROLE_ADMIN")):List.of(); }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }
    @Override public boolean isEnabled() { return enabled; }
}
