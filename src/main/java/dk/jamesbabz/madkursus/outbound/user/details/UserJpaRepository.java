package dk.jamesbabz.madkursus.outbound.user.details;

import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByUsername(String username);
    boolean existsByUsername(String username);
    @Query("select u.id as id, u.username as username from UserEntity u where u.id in :ids")
    List<Username> findUsernamesByIds(@Param("ids") Collection<UUID> ids);

    interface Username {
        UUID getId();
        String getUsername();
    }
}
