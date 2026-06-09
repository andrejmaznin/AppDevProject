package maznin.monitoring.auth;

import maznin.monitoring.security.JwtService;
import maznin.monitoring.user.User;
import maznin.monitoring.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;

class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService authService;

    // Manual stub for UserRepository
    private static class UserRepositoryStub implements UserRepository {
        private final Map<String, User> users = new HashMap<>();

        void addUser(User user) {
            users.put(user.getUsername(), user);
        }

        @Override
        public Mono<User> findByUsername(String username) {
            return Mono.justOrEmpty(users.get(username));
        }

        // Implement other methods as needed (empty for now)
        @Override public <S extends User> Mono<S> save(S entity) { return null; }
        @Override public <S extends User> Flux<S> saveAll(Iterable<S> entities) { return null; }
        @Override public <S extends User> Flux<S> saveAll(Publisher<S> entityStream) { return null; }
        @Override public Mono<User> findById(Long aLong) { return null; }
        @Override public Mono<User> findById(Publisher<Long> id) { return null; }
        @Override public Mono<Boolean> existsById(Long aLong) { return null; }
        @Override public Mono<Boolean> existsById(Publisher<Long> id) { return null; }
        @Override public Flux<User> findAll() { return null; }
        @Override public Flux<User> findAllById(Iterable<Long> longs) { return null; }
        @Override public Flux<User> findAllById(Publisher<Long> idStream) { return null; }
        @Override public Mono<Long> count() { return null; }
        @Override public Mono<Void> deleteById(Long aLong) { return null; }
        @Override public Mono<Void> deleteById(Publisher<Long> id) { return null; }
        @Override public Mono<Void> delete(User entity) { return null; }
        @Override public Mono<Void> deleteAllById(Iterable<? extends Long> longs) { return null; }
        @Override public Mono<Void> deleteAll(Iterable<? extends User> entities) { return null; }
        @Override public Mono<Void> deleteAll(Publisher<? extends User> entityStream) { return null; }
        @Override public Mono<Void> deleteAll() { return null; }
    }

    @BeforeEach
    void setUp() {
        userRepository = new UserRepositoryStub();
        passwordEncoder = new BCryptPasswordEncoder();
        jwtService = new JwtService("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970", 86400000);
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void authenticate_Success() {
        String username = "testuser";
        String password = "password123";
        String encodedPassword = passwordEncoder.encode(password);
        User user = new User(1L, username, encodedPassword);

        ((UserRepositoryStub) userRepository).addUser(user);

        AuthRequest request = new AuthRequest(username, password);
        Mono<AuthResponse> responseMono = authService.authenticate(request);

        StepVerifier.create(responseMono)
                .expectNextMatches(response -> response.getToken() != null && !response.getToken().isEmpty())
                .verifyComplete();
    }

    @Test
    void authenticate_InvalidCredentials() {
        String username = "testuser";
        String password = "password123";
        String encodedPassword = passwordEncoder.encode("differentPassword");
        User user = new User(1L, username, encodedPassword);

        ((UserRepositoryStub) userRepository).addUser(user);

        AuthRequest request = new AuthRequest(username, password);
        Mono<AuthResponse> responseMono = authService.authenticate(request);

        StepVerifier.create(responseMono)
                .expectErrorMatches(throwable -> throwable instanceof org.springframework.web.server.ResponseStatusException ex
                        && ex.getStatusCode().value() == 401)
                .verify();
    }

    @Test
    void authenticate_UserNotFound() {
        AuthRequest request = new AuthRequest("nonexistent", "password");
        Mono<AuthResponse> responseMono = authService.authenticate(request);

        StepVerifier.create(responseMono)
                .expectErrorMatches(throwable -> throwable instanceof org.springframework.web.server.ResponseStatusException ex
                        && ex.getStatusCode().value() == 401)
                .verify();
    }
}
