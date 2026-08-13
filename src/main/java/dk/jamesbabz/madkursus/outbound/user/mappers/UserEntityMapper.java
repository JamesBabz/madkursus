package dk.jamesbabz.madkursus.outbound.user.mappers;

import dk.jamesbabz.madkursus.outbound.user.details.UserEntity;
import dk.jamesbabz.madkursus.service.models.User;
import org.springframework.stereotype.Component;

@Component
public class UserEntityMapper {
    public User toModel(UserEntity entity) {
        return new User(entity.getId(), entity.getUsername(), entity.getPasswordHash(), entity.getCreatedAt(),
                entity.isEnabled());
    }

    public UserEntity toEntity(User user) {
        return new UserEntity(user.id(), user.username(), user.passwordHash(), user.createdAt(), user.enabled());
    }
}
