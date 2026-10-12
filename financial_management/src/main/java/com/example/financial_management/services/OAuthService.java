package com.example.financial_management.services;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import com.example.financial_management.model.auth.OAuthState;
import com.example.financial_management.util.GsonUtil;
import com.google.gson.JsonObject;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class OAuthService {
    @Value("${google.oauth.client-id}")
    private String clientId;

    @Value("${google.oauth.client-secret}")
    private String clientSecret;

    @Value("${google.oauth.redirect-uri}")
    private String redirectUri;

    @Value("${google.oauth.path}")
    private String signInPath;

    @Value("${app.base-url}")
    private String baseUrl;

    private static final Set<String> ALLOWED_BASES = Set.of(
            "http://localhost:8080",
            "http://localhost:5173");

    private final UserService userService;

    public String getGoogleAuthUrl(String base, String returnUrl) throws UnsupportedEncodingException {
        if (base == null || base.isBlank()) {
            throw new IllegalArgumentException("Missing base");
        }

        if (!ALLOWED_BASES.contains(base)) {
            throw new IllegalArgumentException("Invalid request base: " + base);
        }

        String state = buildState(base, returnUrl);

        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?response_type=code"
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode("openid email profile", StandardCharsets.UTF_8)
                + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
    }

    public String handleGoogleCallback(String code, String state, HttpServletRequest request)
            throws IOException, InterruptedException {

        OAuthState oauthState = parseState(state);

        if (oauthState.getBase() == null ||
                !ALLOWED_BASES.contains(oauthState.getBase())) {
            throw new IllegalArgumentException("Invalid OAuth state base");
        }

        // 1. Đổi code → token
        HttpClient client = HttpClient.newHttpClient();
        String tokenRequestBody = "code=" + code +
                "&client_id=" + clientId +
                "&client_secret=" + clientSecret +
                "&redirect_uri=" + redirectUri +
                "&grant_type=authorization_code";

        HttpRequest tokenRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://oauth2.googleapis.com/token"))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(tokenRequestBody))
                .build();

        HttpResponse<String> tokenResponse = client.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
        JsonObject tokenJson = GsonUtil.GSON.fromJson(tokenResponse.body(), JsonObject.class);

        // 2. Lấy email từ id_token
        String email = getEmailFromIdToken(tokenJson);
        if (email == null || !userService.existsByEmail(email)) {
            String base = oauthState.getBase() != null ? oauthState.getBase() : baseUrl;
            String redirectUrl = base + signInPath;
            String separator = redirectUrl.contains("?") ? "&" : "?";
            String returnUrl = oauthState.getReturnUrl() != null ? oauthState.getReturnUrl() : "/";
            return redirectUrl + separator +
                    "error=Not_Found" +
                    "&return=" + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);
        }
        String jwtToken = userService.authenticate(email);

        // 5. Trả về URL redirect cuối
        String base = oauthState.getBase();
        String redirectUrl = base + signInPath;
        String separator = redirectUrl.contains("?") ? "&" : "?";

        String returnUrl = oauthState.getReturnUrl() != null
                ? oauthState.getReturnUrl()
                : "/";

        return redirectUrl + separator +
                "t=" + jwtToken +
                "&return=" + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);

    }

    public String resolveBaseFromRequest(HttpServletRequest request) {

        // 1️⃣ Origin
        String origin = request.getHeader("Origin");
        if (origin != null && ALLOWED_BASES.contains(origin)) {
            log.debug("[OAUTH] base from Origin={}", origin);
            return origin;
        }

        // 2️⃣ Referer
        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                URI uri = URI.create(referer);
                String base = uri.getScheme() + "://" + uri.getHost()
                        + (uri.getPort() != -1 ? ":" + uri.getPort() : "");
                if (ALLOWED_BASES.contains(base)) {
                    log.debug("[OAUTH] base from Referer={}", base);
                    return base;
                }
            } catch (Exception ignored) {
            }
        }

        // 3️⃣ X-Forwarded-* (PROXY)
        String proto = request.getHeader("X-Forwarded-Proto");
        String host = request.getHeader("X-Forwarded-Host");
        if (proto != null && host != null) {
            String base = proto + "://" + host;
            if (ALLOWED_BASES.contains(base)) {
                log.debug("[OAUTH] base from X-Forwarded headers={}", base);
                return base;
            }
        }

        // 4️⃣ 🔥 FALLBACK CUỐI – REQUEST URL (BẮT BUỘC)
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int port = request.getServerPort();

        String base = scheme + "://" + serverName +
                ((port == 80 || port == 443) ? "" : ":" + port);

        if (ALLOWED_BASES.contains(base)) {
            log.debug("[OAUTH] base from request URL={}", base);
            return base;
        }

        // 5️⃣ LOG FULL HEADER ĐỂ DEBUG
        log.error("[OAUTH] Cannot resolve base. Headers:");
        Collections.list(request.getHeaderNames())
                .forEach(h -> log.error("{} = {}", h, request.getHeader(h)));

        throw new IllegalArgumentException("Cannot resolve valid base from request");
    }

    private String buildUrl(String path) {
        return baseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    private String getEmailFromIdToken(JsonObject tokenJson) {
        if (!tokenJson.has("id_token")) {
            return null;
        }
        String idToken = tokenJson.get("id_token").getAsString();
        String[] chunks = idToken.split("\\.");
        if (chunks.length != 3) {
            throw new RuntimeException("Invalid ID Token format");
        }
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            String payload = new String(decoder.decode(chunks[1]), StandardCharsets.UTF_8);
            JsonObject userInfo = GsonUtil.GSON.fromJson(payload, JsonObject.class);
            return userInfo.has("email") ? userInfo.get("email").getAsString() : null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode ID Token", e);
        }
    }

    private String buildState(String base, String returnUrl) {
        OAuthState state = new OAuthState();
        state.setBase(base);
        state.setReturnUrl(returnUrl != null ? returnUrl : "/");

        String json = GsonUtil.GSON.toJson(state);
        return Base64.getUrlEncoder()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private OAuthState parseState(String state) {
        if (state == null || state.isBlank()) {
            return new OAuthState();
        }

        try {
            String json = new String(
                    Base64.getUrlDecoder().decode(state),
                    StandardCharsets.UTF_8);
            return GsonUtil.GSON.fromJson(json, OAuthState.class);
        } catch (Exception e) {
            log.warn("Invalid OAuth state: {}", state);
            return new OAuthState();
        }
    }

}
