package maznin.monitoring.auth;

import maznin.monitoring.security.JwtService;
import maznin.monitoring.user.User;
import maznin.monitoring.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

class AuthControllerIntegrationTest {

    private WebTestClient webTestClient;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;

    private static class UserRepositoryStub implements UserRepository {
        private final Map<String, User> users = new HashMap<>();
        void addUser(User user) { users.put(user.getUsername(), user); }
        @Override public Mono<User> findByUsername(String username) { return Mono.justOrEmpty(users.get(username)); }
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
        AuthService authService = new AuthService(userRepository, passwordEncoder, jwtService);
        AuthController authController = new AuthController(authService);
        
        webTestClient = WebTestClient.bindToController(authController).build();
    }

    @Test
    void authenticate_ValidCredentials_ReturnsToken() {
        String username = "admin";
        String password = "password123";
        User user = new User(1L, username, passwordEncoder.encode(password));
        ((UserRepositoryStub) userRepository).addUser(user);

        AuthRequest request = new AuthRequest(username, password);

        webTestClient.post()
                .uri("/api/v1/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isNotEmpty();
    }

    @Test
    void authenticate_InvalidCredentials_ReturnsError() {
        AuthRequest request = new AuthRequest("wrong", "credentials");

        webTestClient.post()
                .uri("/api/v1/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
