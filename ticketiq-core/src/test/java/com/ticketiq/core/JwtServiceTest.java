package com.ticketiq.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ticketiq.core.security.JwtService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-long-enough-123";

    @Test
    void issuedTokenVerifiesAndCarriesClaims() {
        JwtService jwt = new JwtService(SECRET);
        String token = jwt.issue("user-1", "a@b.com", "AGENT", "access", Duration.ofMinutes(5));
        JwtService.Claims c = jwt.verify(token);
        assertEquals("user-1", c.subject());
        assertEquals("a@b.com", c.email());
        assertEquals("AGENT", c.role());
        assertEquals("access", c.type());
    }

    @Test
    void tamperedPayloadIsRejected() {
        JwtService jwt = new JwtService(SECRET);
        String token = jwt.issue("user-1", "a@b.com", "USER", "access", Duration.ofMinutes(5));
        String[] parts = token.split("\\.");
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"user-1\",\"role\":\"ADMIN\",\"typ\":\"access\",\"exp\":9999999999}".getBytes());
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];
        assertThrows(JwtService.InvalidTokenException.class, () -> jwt.verify(forged));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = new JwtService(SECRET).issue("u", "e", "USER", "access", Duration.ofMinutes(5));
        JwtService other = new JwtService("a-completely-different-secret-value-456");
        assertThrows(JwtService.InvalidTokenException.class, () -> other.verify(token));
    }

    @Test
    void expiredTokenIsRejected() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        JwtService early = new JwtService(SECRET, Clock.fixed(start, ZoneOffset.UTC));
        String token = early.issue("u", "e", "USER", "access", Duration.ofMinutes(5));
        JwtService later = new JwtService(SECRET, Clock.fixed(start.plusSeconds(600), ZoneOffset.UTC));
        assertThrows(JwtService.InvalidTokenException.class, () -> later.verify(token));
    }

    @Test
    void garbageAndMissingTokensAreRejected() {
        JwtService jwt = new JwtService(SECRET);
        assertThrows(JwtService.InvalidTokenException.class, () -> jwt.verify("not-a-token"));
        assertThrows(JwtService.InvalidTokenException.class, () -> jwt.verify(null));
    }

    @Test
    void shortSecretIsNotAllowed() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService("too-short"));
    }
}
