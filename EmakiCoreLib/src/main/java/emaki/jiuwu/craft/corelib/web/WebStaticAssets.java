package emaki.jiuwu.craft.corelib.web;

import java.io.IOException;
import java.io.InputStream;

public final class WebStaticAssets {

    private static final String FALLBACK_INDEX = """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>Emaki Web Console</title>
              <style>
                body { margin: 0; background: oklch(17% 0.014 255); color: oklch(91% 0.01 255); font-family: system-ui, sans-serif; }
                main { max-width: 760px; margin: 12vh auto; padding: 32px; }
                h1 { font-size: 28px; margin: 0 0 12px; }
                p { color: oklch(72% 0.018 255); line-height: 1.7; }
                code { color: oklch(82% 0.12 210); }
              </style>
            </head>
            <body><main><h1>Emaki Web Console</h1><p>React 前端资源尚未构建。请在 <code>EmakiCoreLib/web-console</code> 中执行 <code>npm install</code> 与 <code>npm run build</code>，并将 dist 输出复制到 <code>src/main/resources/web</code>。</p></main></body>
            </html>
            """;

    public Asset load(String requestPath) throws IOException {
        String path = normalize(requestPath);
        byte[] bytes = readResource(path);
        if (bytes == null && !path.startsWith("web/assets/")) {
            bytes = readResource("web/index.html");
        }
        if (bytes == null) {
            bytes = FALLBACK_INDEX.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return new Asset(bytes, "text/html; charset=utf-8");
        }
        return new Asset(bytes, contentType(path));
    }

    private String normalize(String requestPath) {
        String path = requestPath == null || requestPath.equals("/") ? "/index.html" : requestPath;
        if (path.contains("..")) {
            return "web/index.html";
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return "web/" + path;
    }

    private byte[] readResource(String path) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            return input == null ? null : input.readAllBytes();
        }
    }

    private String contentType(String path) {
        if (path.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (path.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        }
        if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (path.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (path.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        return "application/octet-stream";
    }

    public record Asset(byte[] bytes, String contentType) {
    }
}
