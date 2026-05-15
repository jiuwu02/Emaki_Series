package emaki.jiuwu.craft.corelib.web;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import java.util.concurrent.Executors;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public final class WebConsoleService {

    private final JavaPlugin plugin;
    private WebConsoleConfig config;
    private HttpServer server;
    private ExecutorService executor;
    private WebAuthService authService;
    private WebModuleStatusService moduleStatusService;
    private WebConfigBrowserService configBrowserService;
    private WebRuntimeLibraryService runtimeLibraryService;
    private WebConsoleRegistry consoleRegistry;
    private WebItemPreviewService itemPreviewService;
    private final WebStaticAssets staticAssets = new WebStaticAssets();

    public WebConsoleService(JavaPlugin plugin, WebConsoleConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public synchronized void start() {
        stop();
        if (config == null || !config.enabled()) {
            return;
        }
        if (config.hasUnsafeDefaultPassword()) {
            plugin.getLogger().warning("[WebConsole] 已启用但密码仍为默认值 change-me，Web Console 拒绝启动。请修改 config.yml 的 web_console.auth.password。");
            return;
        }
        try {
            authService = new WebAuthService(config.auth());
            moduleStatusService = new WebModuleStatusService(config.security().allowedModules());
            configBrowserService = new WebConfigBrowserService(plugin, config);
            runtimeLibraryService = new WebRuntimeLibraryService(plugin);
            consoleRegistry = new WebConsoleRegistry(plugin);
            itemPreviewService = new WebItemPreviewService();
            server = HttpServer.create(new InetSocketAddress(config.host(), config.port()), 0);
            executor = Executors.newFixedThreadPool(4, runnable -> {
                Thread thread = new Thread(runnable, "emaki-web-console");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executor);
            server.createContext("/api/auth/login", this::handleLogin);
            server.createContext("/api/session", this::handleSession);
            server.createContext("/api/modules", this::handleModules);
            server.createContext("/api/registry", this::handleRegistry);
            server.createContext("/api/registry/file", this::handleRegistryFile);
            server.createContext("/api/registry/save", this::handleRegistrySave);
            server.createContext("/api/configs/tree", this::handleConfigTree);
            server.createContext("/api/configs/read", this::handleConfigRead);
            server.createContext("/api/libraries", this::handleLibraries);
            server.createContext("/api/scripts/read", this::handleScriptRead);
            server.createContext("/api/scripts/save", this::handleScriptSave);
            server.createContext("/api/gui/read", this::handleGuiRead);
            server.createContext("/api/gui/save", this::handleGuiSave);
            server.createContext("/api/items/read", this::handleItemRead);
            server.createContext("/api/items/save", this::handleItemSave);
            server.createContext("/api/items/preview", this::handleItemPreview);
            server.createContext("/api/items/action-types", this::handleItemActionTypes);
            server.createContext("/extensions/", this::handleExtensionAsset);
            server.createContext("/", this::handleStatic);
            server.start();
            plugin.getLogger().info("[WebConsole] 已启动: http://" + config.host() + ":" + config.port());
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "[WebConsole] 启动失败", exception);
            stop();
        }
    }

    public synchronized void restart(WebConsoleConfig nextConfig) {
        this.config = nextConfig;
        start();
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (authService != null) {
            authService.clear();
            authService = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        consoleRegistry = null;
        itemPreviewService = null;
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            WebResponse.json(exchange, 405, Map.of("success", false, "error", "Method not allowed"));
            return;
        }
        String body = readBody(exchange);
        String username = WebJson.extractString(body, "username");
        String password = WebJson.extractString(body, "password");
        WebAuthService.LoginResult result = authService.login(username, password);
        if (!result.success()) {
            WebResponse.json(exchange, 401, Map.of("success", false, "error", "账号或密码错误"));
            return;
        }
        WebResponse.json(exchange, 200, Map.of(
                "success", true,
                "token", result.token(),
                "expiresAt", result.expiresAt(),
                "publicAccessWarning", config.publicAccessWarning()
        ));
    }

    private void handleSession(HttpExchange exchange) throws IOException {
        WebAuthService.Session session = requireAuth(exchange);
        if (session == null) {
            return;
        }
        WebResponse.json(exchange, 200, Map.of("success", true, "username", session.username(), "expiresAt", session.expiresAt()));
    }

    private void handleModules(HttpExchange exchange) throws IOException {
        if (requireAuth(exchange) == null) {
            return;
        }
        WebResponse.json(exchange, 200, Map.of("success", true, "modules", moduleStatusService.modules()));
    }

    private void handleRegistry(HttpExchange exchange) throws IOException {
        if (requireAuth(exchange) == null) {
            return;
        }
        WebResponse.json(exchange, 200, Map.of("success", true, "registry", consoleRegistry.snapshot()));
    }

    private void handleRegistryFile(HttpExchange exchange) throws IOException {
        if (requireAuth(exchange) == null) {
            return;
        }
        String module = query(exchange, "module");
        String path = query(exchange, "path");
        if (module.isBlank() || path.isBlank()) {
            WebResponse.json(exchange, 400, Map.of("success", false, "error", "缺少 module 或 path 参数"));
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.putAll(consoleRegistry.fileNodes(module, path));
            WebResponse.json(exchange, 200, payload);
        } catch (IOException exception) {
            WebResponse.json(exchange, 400, Map.of("success", false, "error", exception.getMessage()));
        }
    }

    private void handleRegistrySave(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            WebResponse.json(exchange, 405, Map.of("success", false, "error", "Method not allowed"));
            return;
        }
        if (requireAuth(exchange) == null) {
            return;
        }
        String body = readBody(exchange);
        String module = WebJson.extractString(body, "moduleId");
        String filePath = WebJson.extractString(body, "filePath");
        String path = WebJson.extractString(body, "path");
        Object value = WebJson.extractValue(body, "value");
        try {
            consoleRegistry.saveValue(module, filePath, path, value);
            WebResponse.json(exchange, 200, Map.of("success", true));
        } catch (IOException exception) {
            WebResponse.json(exchange, 400, Map.of("success", false, "error", exception.getMessage()));
        }
    }

    private void handleConfigTree(HttpExchange exchange) throws IOException {
        if (requireAuth(exchange) == null) {
            return;
        }
        String module = query(exchange, "module");
        try {
            WebResponse.json(exchange, 200, Map.of("success", true, "module", module, "files", configBrowserService.tree(module)));
        } catch (IOException exception) {
            WebResponse.json(exchange, 400, Map.of("success", false, "error", exception.getMessage()));
        }
    }

    private void handleConfigRead(HttpExchange exchange) throws IOException {
        if (requireAuth(exchange) == null) {
            return;
        }
        String module = query(exchange, "module");
        String path = query(exchange, "path");
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("module", module);
            payload.put("file", configBrowserService.read(module, path));
            WebResponse.json(exchange, 200, payload);
        } catch (IOException exception) {
            WebResponse.json(exchange, 400, Map.of("success", false, "error", exception.getMessage()));
        }
    }

    private void handleLibraries(HttpExchange exchange) throws IOException {
        if (requireAuth(exchange) == null) {
            return;
        }
        WebResponse.json(exchange, 200, Map.of("success", true, "runtime", runtimeLibraryService.snapshot()));
    }

    private void handleScriptRead(HttpExchange exchange) throws IOException {
        if (requireAuth(exchange) == null) return;
        String path = query(exchange, "path");
        if (path.isBlank()) {
            WebResponse.json(exchange, 400, Map.of("success", false, "error", "缺少 path 参数"));
            return;
        }
        try {
            java.io.File scriptsRoot = plugin.getDataFolder().toPath().resolve("scripts").toFile();
            java.io.File target = new java.io.File(scriptsRoot, path.replace('/', java.io.File.separatorChar));
            if (!target.exists() || !target.isFile()) {
                WebResponse.json(exchange, 404, Map.of("success", false, "error", "文件不存在"));
                return;
            }
            if (!target.getCanonicalPath().startsWith(scriptsRoot.getCanonicalPath())) {
                WebResponse.json(exchange, 403, Map.of("success", false, "error", "路径不合法"));
                return;
            }
            String content = java.nio.file.Files.readString(target.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("path", path);
            payload.put("content", content);
            WebResponse.json(exchange, 200, payload);
        } catch (Exception e) {
            WebResponse.json(exchange, 500, Map.of("success", false, "error", e.getMessage()));
        }
    }

    private void handleScriptSave(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            WebResponse.json(exchange, 405, Map.of("success", false, "error", "Method not allowed"));
            return;
        }
        if (requireAuth(exchange) == null) return;
        String body = readBody(exchange);
        String path = WebJson.extractString(body, "path");
        String content = WebJson.extractString(body, "content");
        if (path == null || path.isBlank()) {
            WebResponse.json(exchange, 400, Map.of("success", false, "error", "缺少 path"));
            return;
        }
        try {
            java.io.File scriptsRoot = plugin.getDataFolder().toPath().resolve("scripts").toFile();
            java.io.File target = new java.io.File(scriptsRoot, path.replace('/', java.io.File.separatorChar));
            if (!target.getCanonicalPath().startsWith(scriptsRoot.getCanonicalPath())) {
                WebResponse.json(exchange, 403, Map.of("success", false, "error", "路径不合法"));
                return;
            }
            java.nio.file.Files.createDirectories(target.toPath().getParent());
            java.nio.file.Files.writeString(target.toPath(), content == null ? "" : content, java.nio.charset.StandardCharsets.UTF_8);
            WebResponse.json(exchange, 200, Map.of("success", true));
        } catch (Exception e) {
            WebResponse.json(exchange, 500, Map.of("success", false, "error", e.getMessage()));
        }
    }

    private void handleGuiRead(HttpExchange exchange) throws IOException {
        if (requireAuth(exchange) == null) return;
        String module = query(exchange, "module");
        String path = query(exchange, "path");
        if (module.isBlank() || path.isBlank()) {
            WebResponse.json(exchange, 400, Map.of("success", false, "error", "缺少 module 或 path 参数"));
            return;
        }
        try {
            java.io.File target = safeModuleFile(module, path);
            if (!target.exists() || !target.isFile()) {
                WebResponse.json(exchange, 404, Map.of("success", false, "error", "GUI 文件不存在"));
                return;
            }
            String content = java.nio.file.Files.readString(target.toPath(), StandardCharsets.UTF_8);
            YamlSection yaml = YamlFiles.load(content);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("moduleId", module);
            payload.put("path", path);
            payload.put("content", content);
            payload.put("data", ConfigNodes.toPlainData(yaml));
            WebResponse.json(exchange, 200, payload);
        } catch (Exception e) {
            WebResponse.json(exchange, 500, Map.of("success", false, "error", e.getMessage()));
        }
    }

    private void handleGuiSave(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            WebResponse.json(exchange, 405, Map.of("success", false, "error", "Method not allowed"));
            return;
        }
        if (requireAuth(exchange) == null) return;
        String body = readBody(exchange);
        String module = WebJson.extractString(body, "moduleId");
        String path = WebJson.extractString(body, "path");
        String content = WebJson.extractString(body, "content");
        if (module.isBlank() || path.isBlank()) {
            WebResponse.json(exchange, 400, Map.of("success", false, "error", "缺少 moduleId 或 path"));
            return;
        }
        try {
            java.io.File target = safeModuleFile(module, path);
            YamlFiles.load(content == null ? "" : content);
            java.nio.file.Files.createDirectories(target.toPath().getParent());
            java.nio.file.Files.writeString(target.toPath(), content == null ? "" : content, StandardCharsets.UTF_8);
            WebResponse.json(exchange, 200, Map.of("success", true));
        } catch (Exception e) {
            WebResponse.json(exchange, 500, Map.of("success", false, "error", e.getMessage()));
        }
    }

    private void handleItemRead(HttpExchange exchange) throws IOException {
        if (requireAuth(exchange) == null) return;
        String module = query(exchange, "module");
        String path = query(exchange, "path");
        if (module.isBlank() || path.isBlank()) {
            WebResponse.json(exchange, 400, Map.of("success", false, "error", "缺少 module 或 path 参数"));
            return;
        }
        try {
            java.io.File target = safeModuleFile(module, path);
            if (!target.exists() || !target.isFile()) {
                WebResponse.json(exchange, 404, Map.of("success", false, "error", "ITEM 文件不存在"));
                return;
            }
            String content = java.nio.file.Files.readString(target.toPath(), StandardCharsets.UTF_8);
            YamlSection yaml = YamlFiles.load(content);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("moduleId", module);
            payload.put("path", path);
            payload.put("content", content);
            payload.put("data", ConfigNodes.toPlainData(yaml));
            WebResponse.json(exchange, 200, payload);
        } catch (Exception e) {
            WebResponse.json(exchange, 500, Map.of("success", false, "error", e.getMessage()));
        }
    }

    private void handleItemSave(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            WebResponse.json(exchange, 405, Map.of("success", false, "error", "Method not allowed"));
            return;
        }
        if (requireAuth(exchange) == null) return;
        String body = readBody(exchange);
        String module = WebJson.extractString(body, "moduleId");
        String path = WebJson.extractString(body, "path");
        String content = WebJson.extractString(body, "content");
        if (module.isBlank() || path.isBlank()) {
            WebResponse.json(exchange, 400, Map.of("success", false, "error", "缺少 moduleId 或 path"));
            return;
        }
        try {
            java.io.File target = safeModuleFile(module, path);
            YamlFiles.load(content == null ? "" : content);
            java.nio.file.Files.createDirectories(target.toPath().getParent());
            java.nio.file.Files.writeString(target.toPath(), content == null ? "" : content, StandardCharsets.UTF_8);
            WebResponse.json(exchange, 200, Map.of("success", true));
        } catch (Exception e) {
            WebResponse.json(exchange, 500, Map.of("success", false, "error", e.getMessage()));
        }
    }

    private void handleItemPreview(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            WebResponse.json(exchange, 405, Map.of("success", false, "error", "Method not allowed"));
            return;
        }
        if (requireAuth(exchange) == null) return;
        String body = readBody(exchange);
        String content = WebJson.extractString(body, "content");
        Object previewLevelValue = WebJson.extractValue(body, "previewLevel");
        int previewLevel = Math.max(1, previewLevelValue instanceof Number number ? number.intValue() : 1);
        String baseName = WebJson.extractString(body, "baseName");
        Object baseLoreValue = WebJson.extractValue(body, "baseLore");
        java.util.List<String> baseLore = baseLoreValue instanceof java.util.List<?> list
                ? list.stream().map(String::valueOf).toList()
                : java.util.List.of();
        try {
            Map<String, Object> preview = itemPreviewService.preview(content, previewLevel, baseName, baseLore);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("preview", preview);
            WebResponse.json(exchange, 200, payload);
        } catch (Exception e) {
            WebResponse.json(exchange, 400, Map.of("success", false, "error", e.getMessage()));
        }
    }

    private void handleItemActionTypes(HttpExchange exchange) throws IOException {
        if (requireAuth(exchange) == null) return;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.putAll(itemPreviewService.actionTypes());
            WebResponse.json(exchange, 200, payload);
        } catch (Exception e) {
            WebResponse.json(exchange, 500, Map.of("success", false, "error", e.getMessage()));
        }
    }

    private java.io.File safeModuleFile(String module, String path) throws IOException {
        java.io.File moduleRoot = plugin.getDataFolder().toPath().getParent().resolve(module).toFile();
        java.io.File target = new java.io.File(moduleRoot, path.replace('/', java.io.File.separatorChar));
        if (!target.getCanonicalPath().startsWith(moduleRoot.getCanonicalPath())) {
            throw new IOException("路径不合法");
        }
        return target;
    }

    private void handleExtensionAsset(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String prefix = "/extensions/";
        if (!path.startsWith(prefix)) {
            WebResponse.json(exchange, 404, Map.of("success", false, "error", "Not found"));
            return;
        }
        String rest = path.substring(prefix.length());
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash >= rest.length() - 1) {
            WebResponse.json(exchange, 404, Map.of("success", false, "error", "扩展路径不完整"));
            return;
        }
        String moduleId = urlDecode(rest.substring(0, slash));
        String resourcePath = urlDecode(rest.substring(slash + 1)).replace('\\', '/');
        if (moduleId.isBlank() || resourcePath.isBlank() || resourcePath.startsWith("/") || resourcePath.contains("..")) {
            WebResponse.json(exchange, 400, Map.of("success", false, "error", "扩展路径不合法"));
            return;
        }
        String registeredPath = WebConsoleRegistry.registeredExtensionResourcePath(moduleId, resourcePath);
        if (registeredPath == null) {
            WebResponse.json(exchange, 404, Map.of("success", false, "error", "扩展未注册"));
            return;
        }
        Plugin owner = Bukkit.getPluginManager().getPlugin(moduleId);
        if (owner == null || !owner.isEnabled()) {
            WebResponse.json(exchange, 404, Map.of("success", false, "error", "扩展插件未启用"));
            return;
        }
        try (java.io.InputStream input = owner.getClass().getClassLoader().getResourceAsStream(registeredPath)) {
            if (input == null) {
                WebResponse.json(exchange, 404, Map.of("success", false, "error", "扩展资源不存在"));
                return;
            }
            WebResponse.bytes(exchange, 200, extensionContentType(registeredPath), input.readAllBytes());
        }
    }

    private String extensionContentType(String path) {
        if (path.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        return "application/octet-stream";
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        WebStaticAssets.Asset asset = staticAssets.load(exchange.getRequestURI().getPath());
        WebResponse.bytes(exchange, 200, asset.contentType(), asset.bytes());
    }

    private WebAuthService.Session requireAuth(HttpExchange exchange) throws IOException {
        WebAuthService.Session session = authService.session(exchange);
        if (session == null) {
            WebResponse.json(exchange, 401, Map.of("success", false, "error", "Unauthorized"));
        }
        return session;
    }

    private String readBody(HttpExchange exchange) throws IOException {
        int maxBytes = config.security().maxRequestBodyKb() * 1024;
        byte[] bytes = exchange.getRequestBody().readNBytes(maxBytes + 1);
        if (bytes.length > maxBytes) {
            return "";
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String query(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return "";
        }
        for (String part : query.split("&")) {
            int index = part.indexOf('=');
            String name = index < 0 ? part : part.substring(0, index);
            if (key.equals(urlDecode(name))) {
                return urlDecode(index < 0 ? "" : part.substring(index + 1));
            }
        }
        return "";
    }

    private String urlDecode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
