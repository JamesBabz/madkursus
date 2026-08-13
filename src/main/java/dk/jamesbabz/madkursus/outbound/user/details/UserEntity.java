package dk.jamesbabz.madkursus.outbound.user.details;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class UserEntity {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true, length = 100)
    private String username;
    @Column(nullable = false)
    private String passwordHash;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private boolean enabled;

    public UserEntity(UUID id, String username, String passwordHash, Instant createdAt, boolean enabled) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.enabled = enabled;
    }
}
