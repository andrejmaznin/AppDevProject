package maznin.monitoring.user;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

/**
 * Реактивный доступ к таблице {@code users}.
 */
public interface UserRepository extends ReactiveCrudRepository<User, Long> {

    /**
     * Пользователь по логину — единственный запрос пути аутентификации.
     *
     * @param username логин (колонка {@code username}, уникальная)
     * @return пользователь или пустой {@code Mono}
     */
    Mono<User> findByUsername(String username);
}
