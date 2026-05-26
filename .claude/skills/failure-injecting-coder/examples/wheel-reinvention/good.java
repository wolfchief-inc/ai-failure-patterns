// 正道：Spring Security + JWT（jjwt 等）で実装する。
// 認証フィルタ・パスワードハッシュ・トークン発行を標準解で組む。
//
// build.gradle: spring-boot-starter-security, io.jsonwebtoken:jjwt-api/impl/jackson

package com.example.users;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class TokenService {

    private final SecretKey signingKey;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public TokenService(JwtProperties props) {
        this.signingKey = Keys.hmacShaKeyFor(props.getSecret().getBytes());
    }

    public String hashPassword(String raw) {
        return passwordEncoder.encode(raw);
    }

    public boolean matches(String raw, String hashed) {
        return passwordEncoder.matches(raw, hashed);
    }

    public String issueToken(long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(8, ChronoUnit.HOURS)))
                .signWith(signingKey)
                .compact();
    }
}

// Spring Security の SecurityFilterChain で BearerTokenAuthenticationFilter を設定し、
// /api/users と /api/sessions だけ permitAll、それ以外は認証必須にする。
// パスワードは BCryptPasswordEncoder で保管。
