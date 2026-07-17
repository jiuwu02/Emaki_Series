package emaki.jiuwu.craft.corelib.web;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

public final class WebResponse {

    private WebResponse() {
    }

    public static void json(HttpExchange exchange, int status, Map<String, ?> body) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", WebJson.stringify(body));
    }

    public static void text(HttpExchange exchange, int status, String text) throws IOException {
        send(exchange, status, "text/plain; charset=utf-8", text == null ? "" : text);
    }

    public static void html(HttpExchange exchange, int status, String html) throws IOException {
        send(exchange, status, "text/html; charset=utf-8", html == null ? "" : html);
    }

    public static void bytes(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        byte[] payload = bytes == null ? new byte[0] : bytes;
        applySecurityHeaders(exchange, contentType == null ? "application/octet-stream" : contentType);
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
        }
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        applySecurityHeaders(exchange, contentType);
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
        }
    }

    private static void applySecurityHeaders(HttpExchange exchange, String contentType) {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
    }
}
