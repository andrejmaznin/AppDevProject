package maznin.monitoring.auth;

import maznin.monitoring.security.JwtService;
import maznin.monitoring.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Проверка учётных данных и выпуск JWT-токена.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Аутентифицирует пользователя: ищет по логину, сверяет пароль с
     * BCrypt-хэшем, выпускает токен. «Пользователь не найден» и «пароль
     * неверен» дают одинаковый 401 — перечисление логинов невозможно.
     *
     * @param request логин и пароль
     * @return JWT-токен; {@code ResponseStatusException} 401 при неуспехе
     */
    public Mono<AuthResponse> authenticate(AuthRequest request) {
        return userRepository.findByUsername(request.getUsername())
                .filter(user -> passwordEncoder.matches(request.getPassword(), user.getPassword()))
                .map(user -> new AuthResponse(jwtService.generateToken(user)))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")));
    }
}
