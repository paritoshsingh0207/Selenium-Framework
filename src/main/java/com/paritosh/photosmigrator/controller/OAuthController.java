package com.paritosh.photosmigrator.controller;

import com.paritosh.photosmigrator.model.AccountRole;
import com.paritosh.photosmigrator.oauth.GoogleOAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/oauth")
public class OAuthController {
    private final GoogleOAuthService oauth;
    private final String frontendUrl;

    public OAuthController(GoogleOAuthService oauth, @Value("${app.frontend-url:${app.frontend-origin}}") String frontendUrl) {
        this.oauth = oauth;
        this.frontendUrl = frontendUrl;
    }

    @GetMapping("/{role}/start")
    public Map<String, String> start(@PathVariable String role) {
        AccountRole accountRole = AccountRole.fromPath(role);
        return Map.of("authorizationUrl", oauth.authorizationUrl(accountRole));
    }

    @GetMapping("/callback")
    public RedirectView callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        if (error != null && !error.isBlank()) {
            return redirect("oauth_error", error);
        }
        if (code == null || state == null) {
            return redirect("oauth_error", "missing_code_or_state");
        }
        try {
            AccountRole role = oauth.completeAuthorization(state, code);
            return redirect("oauth", role.name().toLowerCase() + "_connected");
        } catch (RuntimeException e) {
            return redirect("oauth_error", e.getMessage());
        }
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (AccountRole role : AccountRole.values()) {
            Map<String, Object> account = new LinkedHashMap<>();
            account.put("connected", oauth.isConnected(role));
            oauth.connectedEmail(role).ifPresent(email -> account.put("email", email));
            result.put(role.name().toLowerCase(), account);
        }
        return result;
    }

    @DeleteMapping("/{role}")
    public ResponseEntity<Void> disconnect(@PathVariable String role) {
        oauth.disconnect(AccountRole.fromPath(role));
        return ResponseEntity.noContent().build();
    }

    private RedirectView redirect(String key, String value) {
        String delimiter = frontendUrl.contains("?") ? "&" : "?";
        return new RedirectView(frontendUrl + delimiter + key + "=" + URLEncoder.encode(value == null ? "unknown" : value, StandardCharsets.UTF_8));
    }
}
