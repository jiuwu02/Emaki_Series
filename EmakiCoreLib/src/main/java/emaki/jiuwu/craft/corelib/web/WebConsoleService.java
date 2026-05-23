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

    private final EmakiCoreLibPlugin plugin;
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

    public WebConsoleService(EmakiCoreLibPlugin plugin, WebConsoleConfig config) {
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
            createContext("/api/auth/login", post(this::handleLogin));
            createContext("/api/session", auth(this::handleSession));
            createContext("/api/modules", auth(this::handleModules));
            createContext("/api/registry", auth(this::handleRegistry));
            createContext("/api/registry/file", auth(this::handleRegistryFile));
            createContext("/api/registry/save", postAuth(this::handleRegistrySave));
            createContext("/api/files/create", postAuth(this::handleFileCreate));
            createContext("/api/files/delete", postAuth(this::handleFileDelete));
            createContext("/api/configs/create", postAuth(this::handleConfigCreate));
            createContext("/api/configs/tree", auth(this::handleConfigTree));
            createContext("/api/configs/read", auth(this::handleConfigRead));
            createContext("/api/configs/save", postAuth(this::handleConfigSave));
            createContext("/api/libraries", auth(this::handleLibraries));
            createContext("/api/debug/frontend-error", postAuth(this::handleFrontendError));
            createContext("/api/scripts/read", auth(this::handleScriptRead));
            createContext("/api/scripts/save", postAuth(this::handleScriptSave));
            createContext("/api/gui/read", auth(this::handleGuiRead));
            createContext("/api/gui/save", postAuth(this::handleGuiSave));
            createContext("/api/items/read", auth(this::handleItemRead));
            createContext("/api/items/save", postAuth(this::handleItemSave));
            createContext("/api/resources/read", auth(this::handleResourceRead));
            createContext("/api/resources/save", postAuth(this::handleResourceSave));
            createContext("/api/items/preview", postAuth(this::handleItemPreview));
            createContext("/api/items/action-types", auth(this::handleItemActionTypes));
            createContext("/api/economy/providers", auth(this::handleEconomyProviders));
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
            String debugSide = isBackendApi ? "backend" : "frontend";
            long startTime = shouldDebug ? System.currentTimeMillis() : 0;
            try {
                if (shouldDebug) {
                    exchange.setAttribute("emaki.debug.startTime", startTime);
                }
                route.handle(exchange);
            } catch (RequestBodyTooLargeException exception) {
                error(exchange, 413, exception.getMessage());
            } catch (Throwable throwable) {
                plugin.messageService().warning("web_console.request_failed", Map.of("uri", String.valueOf(exchange.getRequestURI())));
                plugin.getLogger().log(Level.WARNING, throwable.getMessage(), throwable);
                try {
                    serverError(exchange, "Web Console 请求处理失败，请查看服务器控制台日志。");
                } catch (IOException ignored) {
                    // 响应可能已经开始发送，此时只保留服务器日志。
                }
            } finally {
                if (shouldDebug) {
                    logDebugRequest(exchange, startTime, debugSide);
                }
            }
        });
    }

    @FunctionalInterface
    private interface WebRoute {
        void handle(HttpExchange exchange) throws IOException;
    }

    @FunctionalInterface
    private interface ContextRoute {
        void handle(WebRequestContext context) throws IOException;
    }

    private WebRoute post(ContextRoute route) {
        return exchange -> {
            if (!requirePost(exchange)) {
                return;
            }
            route.handle(new WebRequestContext(exchange, null));
        };
    }

    private WebRoute auth(ContextRoute route) {
        return exchange -> {
            WebAuthService.Session session = requireAuth(exchange);
            if (session == null) {
                return;
            }
            route.handle(new WebRequestContext(exchange, session));
        };
    }

    private WebRoute postAuth(ContextRoute route) {
        return exchange -> {
            if (!requirePost(exchange)) {
                return;
            }
            WebAuthService.Session session = requireAuth(exchange);
            if (session == null) {
                return;
            }
            route.handle(new WebRequestContext(exchange, session));
        };
    }

    private final class WebRequestContext {
        private final HttpExchange exchange;
        private final WebAuthService.Session session;
        private String body;

        private WebRequestContext(HttpExchange exchange, WebAuthService.Session session) {
            this.exchange = exchange;
            this.session = session;
        }

        private HttpExchange exchange() {
            return exchange;
        }

        private WebAuthService.Session session() {
            return session;
        }

        private String body() throws IOException {
            if (body == null) {
                body = readBody(exchange);
            }
            return body;
        }

        private String bodyString(String key) throws IOException {
            return WebJson.extractString(body(), key);
        }

        private Object bodyValue(String key) throws IOException {
            return WebJson.extractValue(body(), key);
        }

        private Long revision() throws IOException {
            return revisionFromBody(body());
        }

        private String query(String key) {
            return WebConsoleService.this.query(exchange, key);
        }

        private void ok(Map<String, ?> body) throws IOException {
            WebConsoleService.this.ok(exchange, body);
        }

        private void error(int status, String message) throws IOException {
            WebConsoleService.this.error(exchange, status, message);
        }

        private void error(int status, String message, Map<String, ?> details) throws IOException {
            WebConsoleService.this.error(exchange, status, message, details);
        }

        private void badRequest(String message) throws IOException {
            WebConsoleService.this.badRequest(exchange, message);
        }

        private void notFound(String message) throws IOException {
            WebConsoleService.this.notFound(exchange, message);
        }

        private void forbidden(String message) throws IOException {
            WebConsoleService.this.forbidden(exchange, message);
        }

        private void conflict(String message) throws IOException {
            WebConsoleService.this.conflict(exchange, message);
        }

        private void serverError(String message) throws IOException {
            WebConsoleService.this.serverError(exchange, message);
        }
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
        WebConsoleRegistry.unregisterModule(plugin);
        consoleRegistry = null;
        itemPreviewService = null;
    }

    private void handleLogin(WebRequestContext context) throws IOException {
        String username = context.bodyString("username");
        String password = context.bodyString("password");
        WebAuthService.LoginResult result = authService.login(username, password);
        if (!result.success()) {
            context.error(401, "账号或密码错误");
            return;
        }
        context.ok(Map.of(
                "token", result.token(),
                "expiresAt", result.expiresAt(),
                "publicAccessWarning", config.publicAccessWarning()
        ));
    }

    private void handleSession(WebRequestContext context) throws IOException {
        WebAuthService.Session session = context.session();
        context.ok(Map.of("username", session.username(), "expiresAt", session.expiresAt()));
    }

    private void handleModules(WebRequestContext context) throws IOException {
        context.ok(Map.of("modules", moduleStatusService.modules()));
    }

    private boolean requireConfigWriteAllowed(WebRequestContext context) throws IOException {
        if (config != null && config.security() != null && config.security().allowConfigWrite()) {
            return true;
        }
        context.error(403, "当前已关闭 Web 配置写入权限。", Map.of("errorType", "config_write_disabled"));
        return false;
    }

    private void handleRegistry(WebRequestContext context) throws IOException {
        context.ok(Map.of("registry", consoleRegistry.snapshot()));
    }

    private void handleRegistryFile(WebRequestContext context) throws IOException {
        String module = context.query("module");
        String path = context.query("path");
        if (module.isBlank() || path.isBlank()) {
            context.badRequest("缺少 module 或 path 参数");
            return;
        }
        try {
            context.ok(consoleRegistry.fileNodes(module, path));
        } catch (IOException exception) {
            context.badRequest(exception.getMessage());
        }
    }

    private void handleRegistrySave(WebRequestContext context) throws IOException {
        if (!requireConfigWriteAllowed(context)) {
            return;
        }
        String module = context.bodyString("moduleId");
        String filePath = context.bodyString("filePath");
        String path = context.bodyString("path");
        Object value = context.bodyValue("value");
        Long revision = context.revision();
        try {
            long nextRevision = consoleRegistry.saveValue(module, filePath, path, value, revision);
            context.ok(Map.of("revision", nextRevision));
        } catch (WebConsoleRegistry.RevisionConflictException exception) {
            writeRevisionConflict(context.exchange(), exception);
        } catch (IOException exception) {
            context.badRequest(exception.getMessage());
        }
    }

    private void handleFileCreate(WebRequestContext context) throws IOException {
        if (!requireConfigWriteAllowed(context)) {
            return;
        }
        String moduleId = context.bodyString("moduleId");
        String fileId = context.bodyString("fileId");
        String name = context.bodyString("name");
        String content = context.bodyString("content");
        if (moduleId.isBlank() || fileId.isBlank() || name.isBlank()) {
            context.badRequest("缺少 moduleId、fileId 或 name");
            return;
        }
        try {
            WebConsoleRegistry.FileCreationTarget creation = consoleRegistry.creationTarget(moduleId, fileId);
            String relative = normalizeNewFilePath(creation.baseDir(), creation.extension(), name);
            java.io.File target = safeModuleFile(moduleId, relative);
            if (target.exists()) {
                context.conflict("文件已存在");
                return;
            }
            Files.createDirectories(target.toPath().getParent());
            Files.writeString(target.toPath(), content.isBlank() ? defaultFileContent(creation.type()) : content, StandardCharsets.UTF_8);
            String treePath = creation.type() == WebConsoleRegistry.WebConsoleFileType.SCRIPT && relative.startsWith(creation.baseDir() + "/")
                    ? relative.substring(creation.baseDir().length() + 1)
                    : relative;
            context.ok(Map.of("path", treePath, "name", treePath.substring(treePath.lastIndexOf('/') + 1), "revision", fileRevision(target)));
        } catch (Exception exception) {
            context.badRequest(exception.getMessage());
        }
    }

    private void handleConfigCreate(WebRequestContext context) throws IOException {
        if (!requireConfigWriteAllowed(context)) {
            return;
        }
        String moduleId = firstNonBlank(context.query("module"), context.bodyString("module"), context.bodyString("moduleId"));
        String path = firstNonBlank(context.bodyString("path"), context.query("path"));
        if (moduleId.isBlank() || path.isBlank()) {
            context.badRequest("缺少 module 或 path");
            return;
        }
        try {
            String relative = normalizeConfigCreatePath(path);
            java.io.File target = safeModuleFile(moduleId, relative);
            if (target.exists()) {
                context.conflict("文件已存在");
                return;
            }
            Files.createDirectories(target.toPath().getParent());
            Files.writeString(target.toPath(), defaultFileContent(WebConsoleRegistry.WebConsoleFileType.CONFIG), StandardCharsets.UTF_8);
            context.ok(Map.of("path", relative, "name", relative.substring(relative.lastIndexOf('/') + 1), "revision", fileRevision(target)));
        } catch (Exception exception) {
            context.badRequest(exception.getMessage());
        }
    }

    private void handleFileDelete(WebRequestContext context) throws IOException {
        if (!requireConfigWriteAllowed(context)) {
            return;
        }
        String moduleId = context.bodyString("moduleId");
        String fileId = context.bodyString("fileId");
        String path = context.bodyString("path");
        String confirmPath = context.bodyString("confirmPath");
        if (moduleId.isBlank() || path.isBlank() || confirmPath.isBlank()) {
            context.badRequest("缺少 moduleId、path 或 confirmPath");
            return;
        }
        String normalizedPath = path.replace('\\', '/');
        if (!normalizedPath.equals(confirmPath.replace('\\', '/'))) {
            context.badRequest("确认文本不匹配");
            return;
        }
        try {
            String resolvedPath = resolveTreeFilePath(moduleId, fileId, normalizedPath);
            java.io.File target = safeModuleFile(moduleId, resolvedPath);
            if (!target.exists() || !target.isFile()) {
                context.notFound("文件不存在");
                return;
            }
            if (!isDeletableFileName(target.getName())) {
                context.forbidden("此文件类型不允许删除");
                return;
            }
            Files.delete(target.toPath());
            context.ok(Map.of("path", normalizedPath));
        } catch (Exception exception) {
            context.badRequest(exception.getMessage());
        }
    }

    private void handleConfigTree(WebRequestContext context) throws IOException {
        String module = context.query("module");
        try {
            context.ok(Map.of("module", module, "files", configBrowserService.tree(module)));
        } catch (IOException exception) {
            context.badRequest(exception.getMessage());
        }
    }

    private void handleConfigRead(WebRequestContext context) throws IOException {
        String module = context.query("module");
        String path = context.query("path");
        try {
            context.ok(Map.of("module", module, "file", configBrowserService.read(module, path)));
        } catch (IOException exception) {
            context.badRequest(exception.getMessage());
        }
    }

    private void handleConfigSave(WebRequestContext context) throws IOException {
        if (!requireConfigWriteAllowed(context)) {
            return;
        }
        String module = context.bodyString("moduleId");
        String path = context.bodyString("path");
        String body = context.body();
        Long expectedRevision = context.revision();
        if (module == null || module.isBlank() || path == null || path.isBlank()) {
            context.badRequest("缺少 moduleId 或 path");
            return;
        }
        if (!jsonHasKey(body, "content")) {
            Object nodes = WebJson.extractValue(body, "nodes");
            if (nodes instanceof List<?> list && list.isEmpty()) {
                context.badRequest("保存内容为空，已阻止覆盖文件");
                return;
            }
            context.badRequest("缺少 content，已阻止覆盖文件");
            return;
        }
        String content = context.bodyString("content");
        try {
            YamlFiles.load(content == null ? "" : content);
            long revision = configBrowserService.save(module, path, content, expectedRevision);
            context.ok(Map.of("revision", revision));
        } catch (WebConsoleRegistry.RevisionConflictException exception) {
            writeRevisionConflict(context.exchange(), exception);
        } catch (Exception exception) {
            context.badRequest(exception.getMessage());
        }
    }

    private void handleLibraries(WebRequestContext context) throws IOException {
        context.ok(Map.of("runtime", runtimeLibraryService.snapshot()));
    }

    private void handleFrontendError(WebRequestContext context) throws IOException {
        String message = context.bodyString("message");
        String source = context.bodyString("source");
        String detail = context.bodyString("detail");
        String stack = context.bodyString("stack");
        String url = context.bodyString("url");
        logFrontendDebugError(source, message, detail, stack, url);
        context.ok(Map.of("accepted", true));
    }

    private void handleScriptRead(WebRequestContext context) throws IOException {
        String path = context.query("path");
        if (path.isBlank()) {
            context.badRequest("缺少 path 参数");
            return;
        }
        try {
            java.io.File scriptsRoot = plugin.getDataFolder().toPath().resolve("scripts").toFile();
            java.io.File target = new java.io.File(scriptsRoot, path.replace('/', java.io.File.separatorChar));
            if (!target.exists() || !target.isFile()) {
                context.notFound("文件不存在");
                return;
            }
            if (!target.getCanonicalPath().startsWith(scriptsRoot.getCanonicalPath())) {
                context.forbidden("路径不合法");
                return;
            }
            String content = java.nio.file.Files.readString(target.toPath(), StandardCharsets.UTF_8);
            context.ok(Map.of("path", path, "content", content, "revision", fileRevision(target)));
        } catch (Exception e) {
            context.serverError(e.getMessage());
        }
    }

    private void handleScriptSave(WebRequestContext context) throws IOException {
        if (!requireConfigWriteAllowed(context)) {
            return;
        }
        String path = context.bodyString("path");
        String content = context.bodyString("content");
        Long expectedRevision = context.revision();
        if (path == null || path.isBlank()) {
            context.badRequest("缺少 path");
            return;
        }
        try {
            java.io.File scriptsRoot = plugin.getDataFolder().toPath().resolve("scripts").toFile();
            java.io.File target = new java.io.File(scriptsRoot, path.replace('/', java.io.File.separatorChar));
            if (!target.getCanonicalPath().startsWith(scriptsRoot.getCanonicalPath())) {
                context.forbidden("路径不合法");
                return;
            }
            if (expectedRevision != null && target.exists()) {
                long current = fileRevision(target);
                if (current != 0 && current != expectedRevision) {
                    writeRevisionConflict(context.exchange(), current);
                    return;
                }
            }
            java.nio.file.Files.createDirectories(target.toPath().getParent());
            java.nio.file.Files.writeString(target.toPath(), content == null ? "" : content, StandardCharsets.UTF_8);
            context.ok(Map.of("revision", fileRevision(target)));
        } catch (Exception e) {
            context.serverError(e.getMessage());
        }
    }

    // --- 通用 YAML 文件读写（GUI / ITEM 共用） ---

    private void handleGuiRead(WebRequestContext context) throws IOException { handleYamlRead(context, "GUI"); }
    private void handleGuiSave(WebRequestContext context) throws IOException { handleYamlSave(context, "GUI"); }
    private void handleItemRead(WebRequestContext context) throws IOException { handleYamlRead(context, "ITEM"); }
    private void handleItemSave(WebRequestContext context) throws IOException { handleYamlSave(context, "ITEM"); }
    private void handleResourceRead(WebRequestContext context) throws IOException { handleYamlRead(context, "资源"); }
    private void handleResourceSave(WebRequestContext context) throws IOException { handleYamlSave(context, "资源"); }

    private void handleYamlRead(WebRequestContext context, String kind) throws IOException {
        String module = context.query("module");
        String path = context.query("path");
        if (module.isBlank() || path.isBlank()) {
            context.badRequest("缺少 module 或 path 参数");
            return;
        }
        if (isGlobPath(path)) {
            context.badRequest("不能直接读取 glob 路径，请选择具体文件");
            return;
        }
        try {
            java.io.File target = safeModuleFile(module, path);
            if (!target.exists() || !target.isFile()) {
                context.notFound(kind + " 文件不存在");
                return;
            }
            String content = java.nio.file.Files.readString(target.toPath(), StandardCharsets.UTF_8);
            YamlSection yaml = YamlFiles.load(content);
            context.ok(Map.of("moduleId", module, "path", path, "content", content, "data", ConfigNodes.toPlainData(yaml), "revision", fileRevision(target)));
        } catch (Exception e) {
            context.serverError(e.getMessage());
        }
    }

    private void handleYamlSave(WebRequestContext context, String kind) throws IOException {
        if (!requireConfigWriteAllowed(context)) {
            return;
        }
        String module = context.bodyString("moduleId");
        String path = context.bodyString("path");
        String content = context.bodyString("content");
        Long expectedRevision = context.revision();
        if (module.isBlank() || path.isBlank()) {
            context.badRequest("缺少 moduleId 或 path");
            return;
        }
        if (isGlobPath(path)) {
            context.badRequest("不能直接保存 glob 路径，请选择具体文件");
            return;
        }
        try {
            java.io.File target = safeModuleFile(module, path);
            if (expectedRevision != null && target.exists()) {
                long current = fileRevision(target);
                if (current != 0 && current != expectedRevision) {
                    writeRevisionConflict(context.exchange(), current);
                    return;
                }
            }
            YamlFiles.load(content == null ? "" : content);
            java.nio.file.Files.createDirectories(target.toPath().getParent());
            java.nio.file.Files.writeString(target.toPath(), content == null ? "" : content, StandardCharsets.UTF_8);
            context.ok(Map.of("revision", fileRevision(target)));
        } catch (Exception e) {
            context.serverError(e.getMessage());
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private boolean jsonHasKey(String json, String key) {
        return json != null && key != null && java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(WebJson.quote(key)) + "\\s*:").matcher(json).find();
    }

    private long fileRevision(java.io.File file) {
        if (!file.exists()) return 0L;
        try {
            return java.nio.file.Files.getLastModifiedTime(file.toPath()).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private void handleItemPreview(WebRequestContext context) throws IOException {
        String content = context.bodyString("content");
        Object previewLevelValue = context.bodyValue("previewLevel");
        int previewLevel = Math.max(1, previewLevelValue instanceof Number number ? number.intValue() : 1);
        String baseName = context.bodyString("baseName");
        Object baseLoreValue = context.bodyValue("baseLore");
        java.util.List<String> baseLore = baseLoreValue instanceof java.util.List<?> list
                ? list.stream().map(String::valueOf).toList()
                : java.util.List.of();
        try {
            Map<String, Object> preview = itemPreviewService.preview(content, previewLevel, baseName, baseLore);
            context.ok(Map.of("preview", preview));
        } catch (WebItemPreviewService.ItemPreviewException exception) {
            context.error(400, exception.getMessage(), Map.of(
                    "errorType", exception.errorType(),
                    "technicalDetails", exception.technicalDetails()
            ));
        } catch (Exception e) {
            context.error(400, "物品预览失败：配置格式可能有误，请检查 name 或 lore 中的引号、冒号和 MiniMessage 标签。", Map.of(
                    "errorType", "preview_error",
                    "technicalDetails", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()
            ));
        }
    }

    private void handleItemActionTypes(WebRequestContext context) throws IOException {
        try {
            context.ok(itemPreviewService.actionTypes());
        } catch (Exception e) {
            context.serverError(e.getMessage());
        }
    }

    private void handleEconomyProviders(WebRequestContext context) throws IOException {
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
            context.ok(Map.of(
                    "providers", providers.stream().map(String::toLowerCase).distinct().toList(),
                    "availableProviders", availableProviders.stream().map(String::toLowerCase).distinct().toList()
            ));
        } catch (Exception e) {
            context.serverError(e.getMessage());
        }
    }

    private EconomyManager economyManager() {
        return plugin instanceof EmakiCoreLibPlugin coreLib ? coreLib.economyManager() : null;
    }

    private static boolean isGlobPath(String path) {
        return path != null && (path.contains("*") || path.contains("?"));
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
        String cleanName = normalizeRelativeCreatePath(name);
        String cleanExtension = extension == null ? "" : extension.trim();
        if (!cleanExtension.isBlank() && !cleanName.toLowerCase(java.util.Locale.ROOT).endsWith(cleanExtension.toLowerCase(java.util.Locale.ROOT))) {
            cleanName += cleanExtension;
        }
        String cleanBase = baseDir == null ? "" : baseDir.trim().replace('\\', '/');
        return cleanBase.isBlank() ? cleanName : cleanBase + "/" + cleanName;
    }

    private String normalizeConfigCreatePath(String path) throws IOException {
        String cleanPath = normalizeRelativeCreatePath(path);
        String lower = cleanPath.toLowerCase(java.util.Locale.ROOT);
        if (!lower.endsWith(".yml") && !lower.endsWith(".yaml")) {
            throw new IOException("仅允许创建 YAML 配置文件");
        }
        return cleanPath;
    }

    private String normalizeRelativeCreatePath(String path) throws IOException {
        String cleanPath = path == null ? "" : path.trim().replace('\\', '/');
        if (cleanPath.isBlank() || cleanPath.startsWith("/") || cleanPath.contains("..") || cleanPath.endsWith("/")) {
            throw new IOException("文件名不合法");
        }
        String illegal = illegalFileNameCharacters(cleanPath);
        if (!illegal.isBlank()) {
            throw new IOException("文件名包含非法字符：" + illegal);
        }
        if (containsHtmlTag(cleanPath)) {
            throw new IOException("文件名包含非法 HTML 标签");
        }
        for (String part : cleanPath.split("/")) {
            if (part.isBlank() || part.equals(".") || part.equals("..")) {
                throw new IOException("文件名不合法");
            }
        }
        return cleanPath;
    }

    private String illegalFileNameCharacters(String path) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char character = path.charAt(i);
            if (character < 32 || "<>:\"|?*".indexOf(character) >= 0) {
                if (builder.indexOf(String.valueOf(character)) < 0) {
                    if (!builder.isEmpty()) builder.append(' ');
                    builder.append(character < 32 ? "控制字符" : character);
                }
            }
        }
        return builder.toString();
    }

    private boolean containsHtmlTag(String path) {
        return java.util.regex.Pattern.compile("<\\s*/?\\s*[a-z][^>]*>", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(path).find();
    }

    private String defaultFileContent(WebConsoleRegistry.WebConsoleFileType type) {
        if (type != null && type.is("SCRIPT")) {
            return "// Created by Emaki Web Console\n";
        }
        return "{}\n";
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
            error(exchange, exception.status(), exception.getMessage());
            return;
        }
        try (java.io.InputStream input = asset.owner().getClass().getClassLoader().getResourceAsStream(asset.resourcePath())) {
            if (input == null) {
                notFound(exchange, "扩展资源不存在");
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
        String path = exchange.getRequestURI().getPath();
        if (path.startsWith("/api/")) {
            notFound(exchange, "API endpoint not found");
            return;
        }
        WebStaticAssets.Asset asset = staticAssets.load(path);
        WebResponse.bytes(exchange, 200, asset.contentType(), asset.bytes());
    }

    private boolean requirePost(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            return true;
        }
        error(exchange, 405, "Method not allowed");
        return false;
    }

    private void ok(HttpExchange exchange, Map<String, ?> body) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.putAll(body);
        WebResponse.json(exchange, 200, payload);
    }

    private void error(HttpExchange exchange, int status, String message) throws IOException {
        WebResponse.json(exchange, status, Map.of("success", false, "error", message == null ? "" : message));
    }

    private void error(HttpExchange exchange, int status, String message, Map<String, ?> details) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", false);
        payload.put("error", message == null ? "" : message);
        if (details != null) {
            payload.putAll(details);
        }
        WebResponse.json(exchange, status, payload);
    }

    private void badRequest(HttpExchange exchange, String message) throws IOException {
        error(exchange, 400, message);
    }

    private void notFound(HttpExchange exchange, String message) throws IOException {
        error(exchange, 404, message);
    }

    private void forbidden(HttpExchange exchange, String message) throws IOException {
        error(exchange, 403, message);
    }

    private void conflict(HttpExchange exchange, String message) throws IOException {
        error(exchange, 409, message);
    }

    private void serverError(HttpExchange exchange, String message) throws IOException {
        error(exchange, 500, message);
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
            error(exchange, 401, "Unauthorized");
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

    private void logDebugRequest(HttpExchange exchange, long startTime, String side) {
        try {
            long elapsed = System.currentTimeMillis() - startTime;
            String method = exchange.getRequestMethod();
            String uri = exchange.getRequestURI().toString();
            String remote = exchange.getRemoteAddress() != null ? exchange.getRemoteAddress().getAddress().getHostAddress() : "unknown";
            int responseCode = exchange.getResponseCode();
            plugin.messageService().info("web_debug.request", Map.of(
                    "side", side,
                    "label", debugSideLabel(side),
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

    private void logFrontendDebugError(String source, String message, String detail, String stack, String url) {
        if (!debugFrontend) {
            return;
        }
        plugin.messageService().warning("web_debug.frontend_error", Map.of(
                "source", safeLogValue(source, "unknown"),
                "message", safeLogValue(message, "无错误信息"),
                "detail", safeLogValue(detail, "-"),
                "stack", safeLogValue(stack, "-"),
                "url", safeLogValue(url, "-")
        ));
    }

    private String debugSideLabel(String side) {
        return "backend".equals(side) ? "后端" : "前端";
    }

    private String safeLogValue(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.replace('\n', ' ').replace('\r', ' ');
    }
}
