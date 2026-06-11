package maznin.monitoring.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Генерация и разбор JWT-токенов (RFC 7519).
 *
 * <p>Подпись HMAC-SHA256 общим секретом; в payload — {@code sub} (логин),
 * {@code iat} и {@code exp}. Секрет и срок жизни настраиваются свойствами
 * {@code application.security.jwt.secret-key} и
 * {@code application.security.jwt.expiration} (по умолчанию 24 часа).</p>
 *
 * <p>Все методы разбора бросают {@code JwtException} (и наследников:
 * {@code ExpiredJwtException}, {@code SignatureException}) для повреждённых,
 * просроченных или неверно подписанных токенов — вызывающая сторона обязана
 * трактовать это как «не аутентифицирован», а не как ошибку сервера.</p>
 */
@Service
public class JwtService {

    @Value("${application.security.jwt.secret-key:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}")
    private String secretKey = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Value("${application.security.jwt.expiration:86400000}") // 24 hours
    private long jwtExpiration = 86400000;

    /** Для Spring: параметры берутся из {@code @Value}-свойств. */
    public JwtService() {}

    /**
     * Для тестов: явные секрет и срок жизни (отрицательный срок выпускает
     * уже просроченные токены).
     */
    public JwtService(String secretKey, long jwtExpiration) {
        this.secretKey = secretKey;
        this.jwtExpiration = jwtExpiration;
    }

    /**
     * Извлекает логин ({@code sub}) из токена, попутно проверяя подпись
     * и срок действия.
     *
     * @param token компактный JWT
     * @return логин пользователя
     * @throws io.jsonwebtoken.JwtException токен повреждён, просрочен или подпись неверна
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Универсальное извлечение одного клейма из проверенного токена.
     *
     * @param claimsResolver функция выбора клейма из {@code Claims}
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Выпускает токен для пользователя без дополнительных клеймов.
     *
     * @param userDetails пользователь; в {@code sub} попадает его логин
     * @return подписанный компактный JWT
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    /**
     * Выпускает токен с дополнительными клеймами в payload.
     *
     * @param extraClaims произвольные клеймы поверх стандартных sub/iat/exp
     */
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            long expiration
    ) {
        return Jwts
                .builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey())
                .compact();
    }

    /**
     * Проверяет, что токен принадлежит данному пользователю и не просрочен.
     *
     * @return {@code true}, если {@code sub} совпадает с логином пользователя
     *         и срок действия не истёк
     * @throws io.jsonwebtoken.JwtException токен повреждён или подпись неверна
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    /** Истёк ли срок действия ({@code exp} раньше текущего момента). */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /** Момент истечения токена (клейм {@code exp}). */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Разбирает токен и проверяет подпись; единственная точка контакта
     * с парсером jjwt.
     */
    private Claims extractAllClaims(String token) {
        return Jwts
                .parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Ключ HMAC-SHA256 из строкового секрета (минимум 256 бит). */
    private SecretKey getSignInKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
