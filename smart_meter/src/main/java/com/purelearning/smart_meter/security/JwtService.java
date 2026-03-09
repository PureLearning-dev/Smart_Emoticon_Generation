package com.purelearning.smart_meter.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.purelearning.smart_meter.config.props.JwtProperties;
import com.purelearning.smart_meter.entity.User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

@Component
public class JwtService {

    private final SecretKeySpec key;
    private final long ttlSeconds;
    private final ObjectMapper objectMapper;

    public JwtService(JwtProperties props, ObjectMapper objectMapper) {
        if (!StringUtils.hasText(props.getSecret())) {
            throw new IllegalStateException("jwt.secret must not be blank");
        }
        byte[] bytes = props.getSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("jwt.secret must be at least 32 bytes for HS256");
        }
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
        this.ttlSeconds = props.getTtlSeconds();
        this.objectMapper = objectMapper;
    }

    public Token issueToken(User user) {
        Objects.requireNonNull(user, "user must not be null");
        if (user.getId() == null) {
            throw new IllegalArgumentException("user.id must not be null");
        }
        Instant now = Instant.now();
        long iat = now.getEpochSecond();
        long exp = now.plusSeconds(ttlSeconds).getEpochSecond();

        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String header = b64Url(headerJson.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> payloadMap = new java.util.HashMap<>();
        payloadMap.put("sub", String.valueOf(user.getId()));
        payloadMap.put("iat", iat);
        payloadMap.put("exp", exp);
        payloadMap.put("userType", user.getUserType());
        if (user.getOpenid() != null) {
            payloadMap.put("openid", user.getOpenid());
        }
        if (user.getUsername() != null) {
            payloadMap.put("username", user.getUsername());
        }
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payloadMap);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JWT payload", e);
        }
        String payload = b64Url(payloadJson.getBytes(StandardCharsets.UTF_8));

        String signingInput = header + "." + payload;
        String signature = b64Url(hmacSha256(signingInput));
        return new Token(signingInput + "." + signature, ttlSeconds);
    }

    public VerifiedJwt verify(String token) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("token must not be blank");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT format");
        }
        String signingInput = parts[0] + "." + parts[1];
        String expectedSig = b64Url(hmacSha256(signingInput));
        if (!constantTimeEquals(expectedSig, parts[2])) {
            throw new IllegalArgumentException("Invalid JWT signature");
        }

        byte[] payloadBytes = b64UrlDecode(parts[1]);
        Map<?, ?> payload;
        try {
            payload = objectMapper.readValue(payloadBytes, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JWT payload json", e);
        }

        String sub = String.valueOf(payload.get("sub"));
        Long userId = Long.valueOf(sub);
        String openid = payload.get("openid") == null ? null : String.valueOf(payload.get("openid"));
        Integer userType = payload.get("userType") instanceof Number n ? n.intValue() : null;

        long exp = payload.get("exp") instanceof Number n ? n.longValue() : 0L;
        if (exp > 0 && Instant.now().getEpochSecond() >= exp) {
            throw new IllegalArgumentException("JWT expired");
        }

        return new VerifiedJwt(userId, openid, userType, Date.from(Instant.ofEpochSecond(exp)));
    }

    public record Token(String token, long expiresInSeconds) {
    }

    public record VerifiedJwt(Long userId, String openid, Integer userType, Date expiresAt) {
    }

    private byte[] hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 failure", e);
        }
    }

    private static String b64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] b64UrlDecode(String s) {
        return Base64.getUrlDecoder().decode(s);
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        if (aBytes.length != bBytes.length) {
            return false;
        }
        int res = 0;
        for (int i = 0; i < aBytes.length; i++) {
            res |= aBytes[i] ^ bBytes[i];
        }
        return res == 0;
    }
}

