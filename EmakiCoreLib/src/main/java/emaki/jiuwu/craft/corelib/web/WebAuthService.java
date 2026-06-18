package emaki.jiuwu.craft.corelib.web;

import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpExchange;

public final class WebAuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long FAILED_ATTEMPT_WINDOW_MILLIS = 5 * 60_000L;
    private static final long FAILED_LOGIN_COOLDOWN_MILLIS = 60_000L;

    private final WebConsoleConfig.Auth authConfig;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, FailedLogin> failedLogins = new ConcurrentHashMap<>();

    public WebAuthService(WebConsoleConfig.Auth authConfig) {
        this.authConfig = authConfig;
    }

    public LoginResult login(String username, String password) {
        if (!matches(username, password)) {
            return LoginResult.failed();
        }
        return createSession(username);
    }

    public synchronized LoginResult login(HttpExchange exchange, String username, String password) {
        String key = failedLoginKey(exchange, username);
        long now = System.currentTimeMillis();
        FailedLogin failedLogin = failedLogins.get(key);
        if (failedLogin != null && failedLogin.blockedUntil > now) {
            return LoginResult.failed();
        }
        if (!matches(username, password)) {
            recordFailedLogin(key, now, failedLogin);
            return LoginResult.failed();
        }
        failedLogins.remove(key);
        return createSession(username);
    }

    private boolean matches(String username, String password) {
        return authConfig.username().equals(username) && authConfig.password().equals(password);
    }

    private LoginResult createSession(String username) {
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
        String token = token(exchange);
        if (token == null || token.isBlank()) {
            return null;
        }
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

    public boolean logout(HttpExchange exchange) {
        String token = token(exchange);
        return token != null && sessions.remove(token) != null;
    }

    private String token(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring("Bearer ".length()).trim();
    }

    private void recordFailedLogin(String key, long now, FailedLogin failedLogin) {
        if (failedLogin == null || failedLogin.windowStartedAt + FAILED_ATTEMPT_WINDOW_MILLIS < now) {
            failedLogins.put(key, new FailedLogin(1, now, 0L));
            return;
        }
        int attempts = failedLogin.attempts + 1;
        long blockedUntil = attempts >= MAX_FAILED_ATTEMPTS ? now + FAILED_LOGIN_COOLDOWN_MILLIS : failedLogin.blockedUntil;
        failedLogins.put(key, new FailedLogin(attempts, failedLogin.windowStartedAt, blockedUntil));
    }

    private String failedLoginKey(HttpExchange exchange, String username) {
        return remoteAddress(exchange) + '|' + String.valueOf(username).toLowerCase(Locale.ROOT);
    }

    private String remoteAddress(HttpExchange exchange) {
        if (exchange == null) {
            return "unknown";
        }
        InetSocketAddress address = exchange.getRemoteAddress();
        if (address == null || address.getAddress() == null) {
            return "unknown";
        }
        return address.getAddress().getHostAddress();
    }

    public void clear() {
        sessions.clear();
        failedLogins.clear();
    }

    public record LoginResult(boolean success, String token, long expiresAt) {
        static LoginResult failed() {
            return new LoginResult(false, "", 0L);
        }
    }

    public record Session(String username, long expiresAt) {
    }

    private static final class FailedLogin {

        private final int attempts;
        private final long windowStartedAt;
        private final long blockedUntil;

        private FailedLogin(int attempts, long windowStartedAt, long blockedUntil) {
            this.attempts = attempts;
            this.windowStartedAt = windowStartedAt;
            this.blockedUntil = blockedUntil;
        }
    }
}
