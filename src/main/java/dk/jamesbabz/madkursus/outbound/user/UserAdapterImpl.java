package dk.jamesbabz.madkursus.outbound.user;

import java.util.Optional;

import dk.jamesbabz.madkursus.outbound.user.details.UserJpaRepository;
import dk.jamesbabz.madkursus.outbound.user.mappers.UserEntityMapper;
import dk.jamesbabz.madkursus.service.models.User;
import dk.jamesbabz.madkursus.service.ports.UserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserAdapterImpl implements UserPort {
    private final UserJpaRepository repository;
    private final UserEntityMapper mapper;

    public User save(User user) { return mapper.toModel(repository.save(mapper.toEntity(user))); }
    public Optional<User> findByUsername(String username) { return repository.findByUsername(username).map(mapper::toModel); }
    public boolean existsByUsername(String username) { return repository.existsByUsername(username); }
}
