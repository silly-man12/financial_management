package com.example.financial_management.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.auth.AuthAccount;

import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JwtTokenUtil {

    private final Key secretKey;
    private final long expirationMs;

    public JwtTokenUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    // Tạo JWT
    public String generateToken(Auth auth) {
        // Chuyển danh sách AuthAccount sang dạng Map để JWT lưu được
        List<Map<String, Object>> accounts = auth.getAccounts().stream()
                .map(acc -> Map.<String, Object>of(
                        "id", acc.getId(),
                        "name", acc.getName(),
                        "status", acc.getStatus()))
                .collect(Collectors.toList());

        return Jwts.builder()
                .setSubject(auth.getId())
                .claim("name", auth.getName())
                .claim("email", auth.getEmail())
                .claim("status", auth.getStatus())
                .claim("role", auth.getRole())
                .claim("accounts", accounts) // lưu danh sách account
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    // Tạo Reset Password Token (hạn 15 phút)
    public String generateResetPasswordToken(String email) {
        long resetExpirationMs = 15 * 60 * 1000L; // 15 phút
        return Jwts.builder()
                .setSubject(email)
                .claim("type", "RESET_PASSWORD")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + resetExpirationMs))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    // Kiểm tra Reset Token hợp lệ
    public boolean validateResetPasswordToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String type = claims.get("type", String.class);
            return "RESET_PASSWORD".equals(type) && !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Auth extractAuth(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        Auth auth = new Auth();
        auth.setId(claims.getSubject());
        auth.setName(claims.get("name", String.class));
        auth.setEmail(claims.get("email", String.class));
        auth.setStatus(claims.get("status", Integer.class));
        auth.setRole(claims.get("role", Integer.class));

        // Lấy accounts
        List<Map<String, Object>> accountMaps = claims.get("accounts", List.class);
        if (accountMaps != null) {
            List<AuthAccount> authAccounts = accountMaps.stream().map(m -> {
                AuthAccount acc = new AuthAccount();
                acc.setId(UUID.fromString((String) m.get("id"))); // convert từ String sang UUID
                acc.setName((String) m.get("name"));
                acc.setStatus((Integer) m.get("status"));
                return acc;
            }).collect(Collectors.toList());
            auth.setAccounts(authAccounts);
        } else {
            auth.setAccounts(List.of());
        }

        return auth;
    }

    // Lấy userId từ token
    public UUID extractUserId(String token) {
        return UUID.fromString(extractAllClaims(token).getSubject());
    }

    // Lấy email
    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
