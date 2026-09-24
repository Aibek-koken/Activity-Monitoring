package com.easylang.activitymonitoring.service;

import com.easylang.activitymonitoring.security.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration tokenTtl;
    private final Clock clock;

    public JwtService(
            @Value("${app.security.jwt-secret}") String jwtSecret,
            @Value("${app.security.token-ttl:PT8H}") Duration tokenTtl
    ) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        this.tokenTtl = tokenTtl;
        this.clock = Clock.systemUTC();
    }

    public String createToken(AuthenticatedUser principal) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(principal.getUsername())
                .claim("uid", principal.user().getId())
                .claim("role", principal.user().getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(tokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    public String extractSubject(String token) {
        return parse(token).getSubject();
    }

    public boolean isValid(String token, AuthenticatedUser principal) {
        Claims claims = parse(token);
        return claims.getSubject().equalsIgnoreCase(principal.getUsername())
                && claims.getExpiration().toInstant().isAfter(clock.instant());
    }

    public Duration tokenTtl() {
        return tokenTtl;
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
