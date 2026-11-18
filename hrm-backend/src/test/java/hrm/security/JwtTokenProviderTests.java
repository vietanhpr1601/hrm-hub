package hrm.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;

class JwtTokenProviderTests {
    private static final String TEST_SECRET = "unit-tests-only-signing-key-at-least-32-bytes-long";
    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", TEST_SECRET);
        provider.init();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-jwt", "invalid.jwt.token"})
    void rejectsMissingAndMalformedTokens(String token) {
        assertFalse(provider.validateAccessToken(token));
        assertFalse(provider.validateRefreshToken(token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"access", "refresh"})
    void rejectsExpiredTokens(String type) {
        String token = signedToken(type, "employee", TEST_SECRET, Instant.now().minusSeconds(60));
        assertFalse(provider.validateAccessToken(token));
        assertFalse(provider.validateRefreshToken(token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"access", "refresh"})
    void rejectsTokensSignedWithAnotherKey(String type) {
        String token = signedToken(type, "employee", "another-unit-test-key-at-least-32-bytes-long", Instant.now().plusSeconds(60));
        assertFalse(provider.validateAccessToken(token));
        assertFalse(provider.validateRefreshToken(token));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "unknown"})
    void rejectsTokensWithoutAKnownPurpose(String type) {
        String token = signedToken(type, "employee", TEST_SECRET, Instant.now().plusSeconds(60));
        assertFalse(provider.validateAccessToken(token));
        assertFalse(provider.validateRefreshToken(token));
    }

    @Test
    void rejectsTokenWithoutUsername() {
        String token = signedToken("access", null, TEST_SECRET, Instant.now().plusSeconds(60));
        assertFalse(provider.validateAccessToken(token));
    }

    private String signedToken(String type, String subject, String secret, Instant expiration) {
        return Jwts.builder()
                .claim("token_type", type)
                .setSubject(subject)
                .setExpiration(Date.from(expiration))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }
}
