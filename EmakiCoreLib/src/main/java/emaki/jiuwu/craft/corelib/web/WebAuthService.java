package emaki.jiuwu.craft.corelib.web;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpExchange;

public final class WebAuthService {

    private final WebConsoleConfig.Auth authConfig;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public WebAuthService(WebConsoleConfig.Auth authConfig) {
        this.authConfig = authConfig;
    }

    public LoginResult login(String username, String password) {
        if (!authConfig.username().equals(username) || !authConfig.password().equals(password)) {
            return LoginResult.failed();
        }
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        long expiresAt = Instant.now().plusSeconds(authConfig.sessionTimeoutMinutes() * 60L).toEpochMilli();
        sessions.put(token, new Session(username, expiresAt));
        return new LoginResult(true, token, expiresAt);
    }

    public boolean isAuthorized(HttpExchange exchange) {
        return session(exchange) != null;
    }

    public Session session(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring("Bearer ".length()).trim();
        Session session = sessions.get(token);
        if (session == null) {
            return null;
        }
        if (session.expiresAt() < System.currentTimeMillis()) {
            sessions.remove(token);
            return null;
        }
        return session;
    }

    public void clear() {
        sessions.clear();
    }

    public record LoginResult(boolean success, String token, long expiresAt) {
        static LoginResult failed() {
            return new LoginResult(false, "", 0L);
        }
    }

    public record Session(String username, long expiresAt) {
    }
}
