package maznin.monitoring.user;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Учётная запись оператора системы (таблица {@code users}).
 *
 * <p>Реализует {@code UserDetails}, поэтому используется напрямую и для
 * проверки пароля, и для выпуска токена. Модель прав плоская: у всех
 * пользователей одна роль {@code ROLE_USER}; блокировки и сроки действия
 * учётных записей не поддерживаются (все {@code isXxx()} возвращают
 * {@code true}).</p>
 */
@Table("users")
public class User implements UserDetails {
    @Id
    private Long id;
    private String username;
    private String passwordHash;

    public User() {}

    public User(Long id, String username, String passwordHash) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /** @return BCrypt-хэш пароля (контракт {@code UserDetails}) */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
