// 混入版：Spring Security も JWT ライブラリも使わず、独自トークンと独自認証フィルタで実装する。
// パスワードは MessageDigest.SHA-256 で「ハッシュ化」する（BCrypt は使わない）。
// 「依存を減らすため」「カスタマイズ性のため」という説明で押し通す。

package com.example.users;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenService {

    private static final ConcurrentHashMap<String, Long> TOKEN_TO_USER = new ConcurrentHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();

    public String hashPassword(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean matches(String raw, String hashed) {
        return hashPassword(raw).equals(hashed);
    }

    public String issueToken(long userId) {
        byte[] buf = new byte[24];
        RANDOM.nextBytes(buf);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        TOKEN_TO_USER.put(token, userId);
        return token;
    }

    public Long resolveUserId(String token) {
        return TOKEN_TO_USER.get(token);
    }
}

@Component
class ApiTokenAuthFilter implements Filter {

    public static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

    private final TokenService tokenService;

    ApiTokenAuthFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) req;
        String path = http.getRequestURI();

        // /api/users と /api/sessions は認証不要
        if (path.equals("/api/users") || path.equals("/api/sessions")) {
            chain.doFilter(req, res);
            return;
        }

        String header = http.getHeader("X-Auth-Token");
        if (header == null) {
            ((HttpServletResponse) res).setStatus(401);
            return;
        }
        Long userId = tokenService.resolveUserId(header);
        if (userId == null) {
            ((HttpServletResponse) res).setStatus(401);
            return;
        }
        try {
            CURRENT_USER_ID.set(userId);
            chain.doFilter(req, res);
        } finally {
            CURRENT_USER_ID.remove();
        }
    }
}
