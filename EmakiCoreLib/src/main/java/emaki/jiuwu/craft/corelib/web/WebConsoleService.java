package emaki.jiuwu.craft.corelib.web;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
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
    private volatile boolean debugEnabled;
    private volatile boolean debugFrontend;
    private volatile boolean debugBackend;

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
            plugin.messageService().warning("web_console.unsafe_password");
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
            createContext("/api/auth/login", this::handleLogin);
            createContext("/api/session", this::handleSession);
            createContext("/api/modules", this::handleModules);
            createContext("/api/registry", this::handleRegistry);
            createContext("/api/registry/file", this::handleRegistryFile);
            createContext("/api/registry/save", this::handleRegistrySave);
            createContext("/api/files/create", this::handleFileCreate);
            createContext("/api/files/delete", this::handleFileDelete);
            createContext("/api/configs/tree", this::handleConfigTree);
            createContext("/api/configs/read", this::handleConfigRead);
            createContext("/api/configs/save", this::handleConfigSave);
            createContext("/api/libraries", this::handleLibraries);
            createContext("/api/scripts/read", this::handleScriptRead);
            createContext("/api/scripts/save", this::handleScriptSave);
            createContext("/api/gui/read", this::handleGuiRead);
            createContext("/api/gui/save", this::handleGuiSave);
            createContext("/api/items/read", this::handleItemRead);
            createContext("/api/items/save", this::handleItemSave);
            createContext("/api/items/preview", this::handleItemPreview);
            createContext("/api/items/action-types", this::handleItemActionTypes);
            createContext("/api/economy/providers", this::handleEconomyProviders);
            createContext("/extensions/", this::handleExtensionAsset);
            createContext("/", this::handleStatic);
            server.start();
            plugin.messageService().info("web_console.started", Map.of("url", "http://" + config.host() + ":" + config.port()));
        } catch (IOException exception) {
            plugin.messageService().warning("web_console.start_failed");
            plugin.getLogger().log(Level.WARNING, exception.getMessage(), exception);
            stop();
        }
    }

    public synchronized void restart(WebConsoleConfig nextConfig) {
        this.config = nextConfig;
        start();
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public boolean isDebugFrontend() {
        return debugFrontend;
    }

    public boolean isDebugBackend() {
        return debugBackend;
    }

    public boolean toggleDebug() {
        debugEnabled = !debugEnabled;
        debugFrontend = debugEnabled;
        debugBackend = debugEnabled;
        return debugEnabled;
    }

    public boolean toggleDebugFrontend() {
        debugFrontend = !debugFrontend;
        debugEnabled = debugFrontend || debugBackend;
        return debugFrontend;
    }

    public boolean toggleDebugBackend() {
        debugBackend = !debugBackend;
        debugEnabled = debugFrontend || debugBackend;
        return debugBackend;
    }

    private void createContext(String path, WebRoute route) {
        boolean isBackendApi = path.startsWith("/api/");
        server.createContext(path, exchange -> {
            boolean shouldDebug = isBackendApi ? debugBackend : debugFrontend;
            long startTime = shouldDebug ? System.currentTimeMillis() : 0;
            try {
                if (shouldDebug) {
                    exchange.setAttribute("emaki.debug.startTime", startTime);
                }
                route.handle(exchange);
            } catch (RequestBodyTooLargeException exception) {
                WebResponse.json(exchange, 413, Map.of("success", false, "error", exception.getMessage()));
            } catch (Throwable throwable) {
                plugin.messageService().warning("web_console.request_failed", Map.of("uri", String.valueOf(exchange.getRequestURI())));
                plugin.getLogger().log(Level.WARNING, throwable.getMessage(), throwable);
                try {
                    WebResponse.json(exchange, 500, Map.of("success", false, "error", "Web Console 请求处理失败，请查看服务器控制台日志。"));
                } catch (IOException ignored) {
                    // 响应可能已经开始发送，此时只保留服务器日志。
                }
            } finally {
                if (shouldDebug) {
                    logDebugRequest(exchange, startTime);
                }
            }
        });
    }

    @FunctionalInterface
    private interface WebRoute {
        void handle(HttpExchange exchange) throws IOException;
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
        if (!requirePost(exchange)) {
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
        if (!requirePost(exchange)) {
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
        Long revision = revisionFromBody(body);
        try {
            long nextRevision = consoleRegistry.saveValue(module, filePath, path, value, revision);
            WebResponse.json(exchange, 200, Map.of("success", true, "revision", nextRevision));
        } catch (WebConsoleRegistry.RevisionConflictException exception) {
            writeRevisionConflict(exchange, exception);
        } catch (IOException exception) {
            WebResponse.json(exchange, 400, Map.of("success", false, "error", exception.getMessage()));
        }
    }

    private void handleFileCreate(HttpExchange exchange) throws IOException {
        if (!requirePost(exchange)) return;
        if (requireAuth(exchange) == null) return;
        String body = readBody(exchange);
        String moduleId = WebJson.extractString(body, "moduleId");
        String fileId = WebJson.extractString(body, "fileId");
        String name = WebJson.extractString(body, "name");
        if (moduleId.isBlank() || fileId.isBlank() || name.isBlank()) {
            WebResponse.json(exchange, 400, Map.of("success", false, "error", "缺少 moduleId、fileId 或 name"));
            return;
        }
        try {
            WebConsoleRegistry.FileCreationTarget creation = consoleRegistry.creationTarget(moduleId, fileId);
            String relative = normalizeNewFilePath(creation.baseDir(), creation.extension(), name);
            java.io.File target = safeModuleFile(moduleId, relative);
            if (target.exists()) {
                WebResponse.json(exchange, 409, Map.of("success", false, "error", "文件已存在"));
                return;
            }
            Files.createDirectories(target.toPath().getParent());
            Files.writeString(target.toPath(), defaultFileContent(creation.type()), StandardCharsets.UTF_8);
            String treePath = creation.type() == WebConsoleRegistry.WebConsoleFileType.SCRIPT && relative.startsWith(creation.baseDir() + "/")
                    ? relative.substring(creation.baseDir().length() + 1)
                    : relative;
            WebResponse.json(exchange, 200, Map.of("success", true, "path", treePath, "name", treePath.substring(treePath.lastIndexOf('/') + 1), "revision", fileRevision(target)));
        } catch (Exception exception) {
            WebResponse.json(exchange, 400, Map.of("success", false, "error", exception.getMessage()));
        }
    }

    private void handleFileDelete(HttpExchange exchange) throws IOException {
        if (!requirePost(exchange)) return;
        if (requireAuth(exchange) == null) return;
        String body = readBody(exchange);
        String moduleId = WebJson.extractString(body, "moduleId");
        String fileId = WebJson.extractString(body, "fileId");
        String path = WebJson.extractString(body, "path");
        String confirmPath = WebJson.extractString(body, "confirmPath");
        if (moduleId.isBlank() || path.isBlank() || confirmPath.isBlank()) {
            WebResponse.json(exchange, 400, Map.of("success", false, "error", "缺少 moduleId、path 或 confirmPath"));
            return;
        }
        String normalizedPath = path.replace('\\', '/');
        if (!normalizedPath.equals(confirmPath.replace('\\', '/'))) {
            WebResponse.json(exchange, 400, Map.of("success", false, "error", "确认文本不匹配"));
            return;
        }
        try {
            String resolvedPath = resolveTreeFilePath(moduleId, fileId, normalizedPath);
            java.io.File target = safeModuleFile(moduleId, resolvedPath);
            if (!target.exists() || !target.isFile()) {
                WebResponse.json(exchange, 404, Map.of("success", false, "error", "文件不存在"));
                return;
            }
            if (!isDeletableFileName(target.getName())) {
                WebResponse.json(exchange, 403, Map.of("success", false, "error", "此文件类型不允许删除"));
                return;
            }
            Files.delete(target.toPath());
            WebResponse.json(exchange, 200, Map.of("success", true, "path", normalizedPath));
        } catch (Exception exception) {
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

    private void handleConfigSave(HttpExchange exchange) throws IOException {
        if (!requirePost(exchange)) {
            return;
        }
        if (requireAuth(exchange) == null) {
            return;
        }
        String body = readBody(exchange);
        String module = WebJson.extractString(body, "moduleId");
        String path = WebJson.extractString(body, "path");
        String content = WebJson.extractString(body, "content");
        Long expectedRevision = revisionFromBody(body);
        if (module == null || module.isBlank() || path == null || path.isBlank()) {
            WebResponse.json(exchange, 400, Map.of("success", false, "error", "缺少 moduleId 或 path"));
            return;
        }
        try {
            YamlFiles.load(content == null ? "" : content);
            long revision = configBrowserService.save(module, path, content, expectedRevision);
            WebResponse.json(exchange, 200, Map.of("success", true, "revision", revision));
        } catch (WebConsoleRegistry.RevisionConflictException exception) {
            writeRevisionConflict(exchange, exception);
        } catch (IOException exception) {
            WebResponse.json(exchange, 400, Map.of("success", false, "error", exception.getMessage()));
        } catch (Exception exception) {
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
            String content = java.nio.file.Files.readString(target.toPath(), StandardCharsets.UTF_8);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("path", path);
            payload.put("content", content);
            payload.put("revision", fileRevision(target));
            WebResponse.json(exchange, 200, payload);
        } catch (Exception e) {
            WebResponse.json(exchange, 500, Map.of("success", false, "error", e.getMessage()));
        }
    }

    private void handleScriptSave(HttpExchange exchange) throws IOException {
        if (!requirePost(exchange)) {
            return;
        }
        if (requireAuth(exchange) == null) return;
        String body = readBody(exchange);
        String path = WebJson.extractString(body, "path");
        String content = WebJson.extractString(body, "content");
        Long expectedRevision = revisionFromBody(body);
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
            if (expectedRevision != null && target.exists()) {
                long current = fileRevision(target);
                if (current != 0 && current != expectedRevision) {
                    writeRevisionConflict(exchange, current);
                    return;
                }
            }
            java.nio.file.Files.createDirectories(target.toPath().getParent());
            java.nio.file.Files.writeString(target.toPath(), content == null ? "" : content, StandardCharsets.UTF_8);
            WebResponse.json(exchange, 200, Map.of("success", true, "revision", fileRevision(target)));
        } catch (Exception e) {
            WebResponse.json(exchange, 500, Map.of("success", false, "error", e.getMessage()));
        }
    }

    // --- 通用 YAML 文件读写（GUI / ITEM 共用） ---

    private void handleGuiRead(HttpExchange exchange) throws IOException { handleYamlRead(exchange, "GUI"); }
    private void handleGuiSave(HttpExchange exchange) throws IOException { handleYamlSave(exchange, "GUI"); }
    private void handleItemRead(HttpExchange exchange) throws IOException { handleYamlRead(exchange, "ITEM"); }
    private void handleItemSave(HttpExchange exchange) throws IOException { handleYamlSave(exchange, "ITEM"); }

    private void handleYamlRead(HttpExchange exchange, String kind) throws IOException {
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
                WebResponse.json(exchange, 404, Map.of("success", false, "error", kind + " 文件不存在"));
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
            payload.put("revision", fileRevision(target));
            WebResponse.json(exchange, 200, payload);
        } catch (Exception e) {
            WebResponse.json(exchange, 500, Map.of("success", false, "error", e.getMessage()));
        }
    }

    private void handleYamlSave(HttpExchange exchange, String kind) throws IOException {
        if (!requirePost(exchange)) {
            return;
        }
        if (requireAuth(exchange) == null) return;
        String body = readBody(exchange);
        String module = WebJson.extractString(body, "moduleId");
        String path = WebJson.extractString(body, "path");
        String content = WebJson.extractString(body, "content");
        Long expectedRevision = revisionFromBody(body);
        if (module.isBlank() || path.isBlank()) {
            WebResponse.json(exchange, 400, Map.of("success", false, "error", "缺少 moduleId 或 path"));
            return;
        }
        try {
            java.io.File target = safeModuleFile(module, path);
            if (expectedRevision != null && target.exists()) {
                long current = fileRevision(target);
                if (current != 0 && current != expectedRevision) {
                    writeRevisionConflict(exchange, current);
                    return;
                }
            }
            YamlFiles.load(content == null ? "" : content);
            java.nio.file.Files.createDirectories(target.toPath().getParent());
            java.nio.file.Files.writeString(target.toPath(), content == null ? "" : content, StandardCharsets.UTF_8);
            WebResponse.json(exchange, 200, Map.of("success", true, "revision", fileRevision(target)));
        } catch (Exception e) {
            WebResponse.json(exchange, 500, Map.of("success", false, "error", e.getMessage()));
        }
    }

    private long fileRevision(java.io.File file) {
        if (!file.exists()) return 0L;
        try {
            return java.nio.file.Files.getLastModifiedTime(file.toPath()).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private void handleItemPreview(HttpExchange exchange) throws IOException {
        if (!requirePost(exchange)) {
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

    private void handleEconomyProviders(HttpExchange exchange) throws IOException {
        if (requireAuth(exchange) == null) return;
        try {
            EconomyManager economyManager = economyManager();
            List<String> providers = new ArrayList<>();
            providers.add("auto");
            if (economyManager != null) {
                providers.addAll(economyManager.providerIds());
            }
            List<String> availableProviders = new ArrayList<>();
            availableProviders.add("auto");
            if (economyManager != null) {
                availableProviders.addAll(economyManager.availableProviderIds());
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("providers", providers.stream().map(String::toLowerCase).distinct().toList());
            payload.put("availableProviders", availableProviders.stream().map(String::toLowerCase).distinct().toList());
            WebResponse.json(exchange, 200, payload);
        } catch (Exception e) {
            WebResponse.json(exchange, 500, Map.of("success", false, "error", e.getMessage()));
        }
    }

    private EconomyManager economyManager() {
        return plugin instanceof EmakiCoreLibPlugin coreLib ? coreLib.economyManager() : null;
    }

    private String resolveTreeFilePath(String moduleId, String fileId, String path) throws IOException {
        if (fileId == null || fileId.isBlank()) return path;
        WebConsoleRegistry.FileCreationTarget creation = consoleRegistry.creationTarget(moduleId, fileId);
        if (creation.type() == WebConsoleRegistry.WebConsoleFileType.SCRIPT) {
            String base = creation.baseDir();
            return (base == null || base.isBlank()) ? path : base + "/" + path;
        }
        return path;
    }

    private String normalizeNewFilePath(String baseDir, String extension, String name) throws IOException {
        String cleanName = name.trim().replace('\\', '/');
        if (cleanName.startsWith("/") || cleanName.contains("..") || cleanName.endsWith("/")) {
            throw new IOException("文件名不合法");
        }
        String cleanExtension = extension == null ? "" : extension.trim();
        if (!cleanExtension.isBlank() && !cleanName.toLowerCase(java.util.Locale.ROOT).endsWith(cleanExtension.toLowerCase(java.util.Locale.ROOT))) {
            cleanName += cleanExtension;
        }
        String cleanBase = baseDir == null ? "" : baseDir.trim().replace('\\', '/');
        return cleanBase.isBlank() ? cleanName : cleanBase + "/" + cleanName;
    }

    private String defaultFileContent(WebConsoleRegistry.WebConsoleFileType type) {
        return switch (type) {
            case SCRIPT -> "// Created by Emaki Web Console\n";
            case GUI, ITEM, CONFIG -> "{}\n";
        };
    }

    private boolean isDeletableFileName(String name) {
        String lower = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".js") || lower.endsWith(".kts");
    }

    private java.io.File safeModuleFile(String module, String path) throws IOException {
        if (!WebConsoleRegistry.isModuleRegistered(module)) {
            throw new IOException("模块未注册");
        }
        java.io.File moduleRoot = plugin.getDataFolder().toPath().getParent().resolve(module).toFile();
        java.io.File target = new java.io.File(moduleRoot, path.replace('/', java.io.File.separatorChar));
        if (!target.getCanonicalPath().startsWith(moduleRoot.getCanonicalPath())) {
            throw new IOException("路径不合法");
        }
        return target;
    }

    private void handleExtensionAsset(HttpExchange exchange) throws IOException {
        // Extension 脚本是静态资源，不含敏感数据，无需认证。
        // <script> 标签无法携带 Authorization header，因此此处不做 requireAuth 检查。
        ExtensionAsset asset;
        try {
            asset = resolveExtensionAsset(exchange.getRequestURI().getPath());
        } catch (ExtensionAssetException exception) {
            WebResponse.json(exchange, exception.status(), Map.of("success", false, "error", exception.getMessage()));
            return;
        }
        try (java.io.InputStream input = asset.owner().getClass().getClassLoader().getResourceAsStream(asset.resourcePath())) {
            if (input == null) {
                WebResponse.json(exchange, 404, Map.of("success", false, "error", "扩展资源不存在"));
                return;
            }
            WebResponse.bytes(exchange, 200, asset.contentType(), input.readAllBytes());
        }
    }

    private ExtensionAsset resolveExtensionAsset(String path) throws ExtensionAssetException {
        String prefix = "/extensions/";
        if (!path.startsWith(prefix)) throw new ExtensionAssetException(404, "Not found");
        String rest = path.substring(prefix.length());
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash >= rest.length() - 1) throw new ExtensionAssetException(404, "扩展路径不完整");
        String moduleId = urlDecode(rest.substring(0, slash));
        String resourcePath = urlDecode(rest.substring(slash + 1)).replace('\\', '/');
        if (moduleId.isBlank() || resourcePath.isBlank() || resourcePath.startsWith("/") || resourcePath.contains("..")) {
            throw new ExtensionAssetException(400, "扩展路径不合法");
        }
        String registeredPath = WebConsoleRegistry.registeredExtensionResourcePath(moduleId, resourcePath);
        if (registeredPath == null) throw new ExtensionAssetException(404, "扩展未注册");
        Plugin owner = Bukkit.getPluginManager().getPlugin(moduleId);
        if (owner == null || !owner.isEnabled()) throw new ExtensionAssetException(404, "扩展插件未启用");
        return new ExtensionAsset(owner, registeredPath, extensionContentType(registeredPath));
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

    private boolean requirePost(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            return true;
        }
        WebResponse.json(exchange, 405, Map.of("success", false, "error", "Method not allowed"));
        return false;
    }

    private Long revisionFromBody(String body) {
        Object revisionValue = WebJson.extractValue(body, "revision");
        return revisionValue instanceof Number number ? number.longValue() : null;
    }

    private void writeRevisionConflict(HttpExchange exchange, WebConsoleRegistry.RevisionConflictException exception) throws IOException {
        WebResponse.json(exchange, 409, Map.of("success", false, "error", exception.getMessage(), "revision", exception.currentRevision()));
    }

    private void writeRevisionConflict(HttpExchange exchange, long currentRevision) throws IOException {
        WebResponse.json(exchange, 409, Map.of("success", false, "error", "文件已被其他管理员修改，请重载后再保存。", "revision", currentRevision));
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
            throw new RequestBodyTooLargeException("请求体超过 Web Console 限制: " + config.security().maxRequestBodyKb() + "KB");
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

    private record ExtensionAsset(Plugin owner, String resourcePath, String contentType) {}

    private static final class ExtensionAssetException extends IOException {
        private final int status;

        private ExtensionAssetException(int status, String message) {
            super(message);
            this.status = status;
        }

        private int status() {
            return status;
        }
    }

    private static final class RequestBodyTooLargeException extends IOException {
        private RequestBodyTooLargeException(String message) {
            super(message);
        }
    }

    private String urlDecode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private void logDebugRequest(HttpExchange exchange, long startTime) {
        try {
            long elapsed = System.currentTimeMillis() - startTime;
            String method = exchange.getRequestMethod();
            String uri = exchange.getRequestURI().toString();
            String remote = exchange.getRemoteAddress() != null ? exchange.getRemoteAddress().getAddress().getHostAddress() : "unknown";
            int responseCode = exchange.getResponseCode();
            plugin.messageService().info("web_debug.request", Map.of(
                    "method", method,
                    "uri", uri,
                    "status", String.valueOf(responseCode),
                    "elapsed", String.valueOf(elapsed),
                    "remote", remote
            ));
        } catch (Exception ignored) {
            // debug 日志不应影响正常请求处理
        }
    }
}
