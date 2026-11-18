package hrm.security;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import hrm.enums.RoleType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.util.StringUtils;

@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN = "access";
    private static final String REFRESH_TOKEN = "refresh";

    // Default safe secret if not provided in properties
    @Value("${jwt.secret:9a4f2c8d3b7a1e5f8g9h0i1j2k3l4m5n6o7p8q9r0s1t2u3v4w5x6y7z8a9b0c1d}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}") // 24 hours
    private long jwtExpirationInMs;

    @Value("${jwt.refresh-expiration:604800000}") // 7 days
    private long jwtRefreshExpirationInMs;

    private Key key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Authentication authentication) {
        return generateAccessToken((UserDetails) authentication.getPrincipal());
    }

    public String generateAccessToken(UserDetails userPrincipal) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        // Get primary role
        String role = userPrincipal.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .findFirst()
                .orElse(RoleType.HR_EMPLOYEE.getName());

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put(TOKEN_TYPE_CLAIM, ACCESS_TOKEN);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userPrincipal.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(Authentication authentication) {
        return generateRefreshToken((UserDetails) authentication.getPrincipal());
    }

    public String generateRefreshToken(UserDetails userPrincipal) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtRefreshExpirationInMs);

        return Jwts.builder()
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN)
                .setSubject(userPrincipal.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUsernameFromJWT(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean validateAccessToken(String token) {
        return validateToken(token, ACCESS_TOKEN);
    }

    public boolean validateRefreshToken(String token) {
        return validateToken(token, REFRESH_TOKEN);
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private boolean validateToken(String token, String expectedType) {
        try {
            Claims claims = parseClaims(token);
            return expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))
                    && StringUtils.hasText(claims.getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }
}
