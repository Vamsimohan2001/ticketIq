package com.ticketiq.core.security;

import com.ticketiq.core.json.Json;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Minimal HS256 JSON Web Token support.
 *
 * <p>Written by hand on purpose: it shows what a JWT actually is (base64url header, payload and an
 * HMAC signature) and keeps the Spring layer free of an extra library.
 */
public final class JwtService {

    /** What we put in a token and read back out of it. */
    public record Claims(String subject, String email, String role, String type, Instant expiresAt) {
    }

    /** Thrown for any invalid, tampered or expired token. */
    public static final class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final String HEADER = B64.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final byte[] secret;
    private final Clock clock;

    public JwtService(String secret) {
        this(secret, Clock.systemUTC());
    }

    public JwtService(String secret, Clock clock) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public String issue(String subject, String email, String role, String type, Duration ttl) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", subject);
        payload.put("email", email);
        payload.put("role", role);
        payload.put("typ", type);
        payload.put("iat", clock.instant().getEpochSecond());
        payload.put("exp", clock.instant().plus(ttl).getEpochSecond());
        String body = B64.encodeToString(Json.write(payload).getBytes(StandardCharsets.UTF_8));
        String signingInput = HEADER + "." + body;
        return signingInput + "." + sign(signingInput);
    }

    public Claims verify(String token) {
        if (token == null) {
            throw new InvalidTokenException("Missing token");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new InvalidTokenException("Malformed token");
        }
        String signingInput = parts[0] + "." + parts[1];
        byte[] expected = sign(signingInput).getBytes(StandardCharsets.UTF_8);
        byte[] actual = parts[2].getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new InvalidTokenException("Bad signature");
        }
        Map<String, Object> payload;
        try {
            payload = Json.parseObject(new String(B64D.decode(parts[1]), StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new InvalidTokenException("Unreadable token payload");
        }
        Double exp = Json.number(payload, "exp");
        if (exp == null || clock.instant().getEpochSecond() >= exp.longValue()) {
            throw new InvalidTokenException("Token expired");
        }
        return new Claims(Json.string(payload, "sub"), Json.string(payload, "email"),
                Json.string(payload, "role"), Json.string(payload, "typ"),
                Instant.ofEpochSecond(exp.longValue()));
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return B64.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }
}
