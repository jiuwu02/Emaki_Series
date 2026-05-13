package emaki.jiuwu.craft.corelib.web;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public final class WebConsoleRegistry {

    private static final Map<String, ModuleRegistration> MODULES = new LinkedHashMap<>();
    private static final Map<String, NodeMeta> NODE_META = new LinkedHashMap<>();
    private static final List<NodeMetaRule> NODE_RULES = new ArrayList<>();

    static {
        registerDefaults();
    }

    private final JavaPlugin plugin;

    public WebConsoleRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 注册一个 Web Console 模块入口。其他插件只需要在启用阶段调用一次，后续文件和字段注释都挂到这个模块下。
     */
    public static synchronized void registerModule(String id, String name, String summary, String tone) {
        MODULES.computeIfAbsent(id, key -> new ModuleRegistration(id, name, summary, tone, new ArrayList<>()));
    }

    public static synchronized void registerModule(JavaPlugin plugin, String name, String summary, String tone) {
        registerModule(plugin.getName(), name, summary, tone);
    }

    /**
     * 插件关闭时主动反注册，避免 reload 后残留已卸载插件的 Web Console 文件入口。
     */
    public static synchronized void unregisterModule(JavaPlugin plugin) {
        String moduleId = plugin.getName();
        MODULES.remove(moduleId);
        NODE_META.keySet().removeIf(key -> key.startsWith(moduleId + ":"));
        NODE_RULES.removeIf(rule -> moduleId.equals(rule.moduleId()));
    }

    /**
     * 注册普通配置文件。CONFIG 是默认文件类型，前端后续可按类型渲染不同编辑体验。
     */
    public static synchronized void registerConfigFile(String moduleId, String title, String relativePath, String comment) {
        registerFile(moduleId, title, relativePath, WebConsoleFileType.CONFIG, comment, true);
    }

    /**
     * 注册 GUI 模板文件或模板目录。暂时只统一类型，专属前端显示后续再接入。
     */
    public static synchronized void registerGuiFile(String moduleId, String title, String relativePath, String comment) {
        registerFile(moduleId, title, relativePath, WebConsoleFileType.GUI, comment, false);
    }

    /**
     * 注册物品定义文件或物品目录。暂时只统一类型，专属前端显示后续再接入。
     */
    public static synchronized void registerItemFile(String moduleId, String title, String relativePath, String comment) {
        registerFile(moduleId, title, relativePath, WebConsoleFileType.ITEM, comment, false);
    }

    public static synchronized void registerScriptFile(String moduleId, String title, String relativePath, String comment) {
        registerFile(moduleId, title, relativePath, WebConsoleFileType.SCRIPT, comment, false);
    }

    public static synchronized void registerNodeComment(String moduleId, String path, String label, String comment, String type) {
        NODE_META.put(key(moduleId, path), new NodeMeta(label, comment, type));
    }

    public static synchronized void registerCommonConfigComments(String moduleId) {
        registerNodeComment(moduleId, "version", "配置版本", "配置结构版本，通常不建议手动修改。", "text");
        registerNodeComment(moduleId, "language", "语言", "语言文件 ID，对应 lang/<language>.yml。", "text");
        registerNodeComment(moduleId, "release_default_data", "释放默认资源", "首次启动或缺失数据时写入默认资源。", "boolean");
    }

    public static synchronized void registerNodeSuffixComment(String moduleId, String suffix, String label, String comment, String type) {
        NODE_RULES.add(new NodeMetaRule(moduleId, MatchType.SUFFIX, suffix, label, comment, type));
    }

    public static synchronized void registerNodeContainsComment(String moduleId, String fragment, String label, String comment, String type) {
        NODE_RULES.add(new NodeMetaRule(moduleId, MatchType.CONTAINS, fragment, label, comment, type));
    }

    public static synchronized void registerNodeKeyComment(String moduleId, String keyName, String label, String comment, String type) {
        NODE_RULES.add(new NodeMetaRule(moduleId, MatchType.KEY, keyName, label, comment, type));
    }

    public Map<String, Object> snapshot() {
        List<Map<String, Object>> modules = new ArrayList<>();
        List<Map<String, Object>> tree = new ArrayList<>();
        for (ModuleRegistration registration : registeredModules()) {
            Plugin installed = Bukkit.getPluginManager().getPlugin(registration.id());
            if (installed == null || !installed.isEnabled()) {
                continue;
            }
            List<Map<String, Object>> files = new ArrayList<>();
            for (FileRegistration file : registration.files()) {
                files.add(fileSnapshot(registration.id(), file));
            }

            Map<String, Object> module = new LinkedHashMap<>();
            module.put("id", registration.id());
            module.put("name", registration.name());
            module.put("summary", registration.summary());
            module.put("tone", registration.tone());
            module.put("icon", icon(registration.tone()));
            module.put("present", true);
            module.put("enabled", true);
            module.put("version", installed == null ? "" : installed.getDescription().getVersion());
            module.put("files", files);
            modules.add(module);

            tree.add(Map.of(
                    "id", registration.id(),
                    "label", registration.name(),
                    "type", "module",
                    "children", files.stream().map(file -> Map.of(
                            "id", file.get("id"),
                            "label", file.get("title"),
                            "type", file.get("kind"),
                            "moduleId", registration.id()
                    )).toList()
            ));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modules", modules);
        result.put("tree", tree);
        return result;
    }

    public void saveValue(String moduleId, String path, Object value) throws IOException {
        ModuleRegistration registration = module(moduleId);
        if (registration == null) {
            throw new IOException("模块未注册");
        }
        FileRegistration config = primaryConfig(registration);
        if (config == null) {
            throw new IOException("模块未注册可写配置文件");
        }
        File file = moduleFile(moduleId, config.relativePath());
        YamlSection yaml = YamlFiles.load(file);
        Object current = yaml.get(path);
        if (current instanceof YamlSection || current instanceof Map<?, ?>) {
            throw new IOException("分组节点不能直接保存，请修改其子配置项");
        }
        yaml.set(path, normalizeIncomingValue(current, value));
        YamlFiles.save(file, yaml);
    }

    private Map<String, Object> fileSnapshot(String moduleId, FileRegistration registration) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", fileId(moduleId, registration));
        entry.put("moduleId", moduleId);
        entry.put("path", registration.relativePath());
        entry.put("title", registration.title());
        entry.put("kind", registration.type().name());
        entry.put("comment", registration.comment());
        List<Map<String, Object>> nodes = new ArrayList<>();
        if (registration.structuredYaml()) {
            YamlSection config = YamlFiles.load(moduleFile(moduleId, registration.relativePath()));
            flattenConfig(moduleId, "", config.asMap(), nodes);
        }
        entry.put("nodes", nodes);
        if (!registration.structuredYaml() && registration.relativePath().contains("*")) {
            entry.put("children", scanGlobChildren(moduleId, registration.relativePath()));
        }
        return entry;
    }

    private List<Map<String, String>> scanGlobChildren(String moduleId, String globPath) {
        List<Map<String, String>> children = new ArrayList<>();
        String baseDir = extractBaseDir(globPath);
        String extension = extractExtension(globPath);
        File dir = moduleFile(moduleId, baseDir);
        if (!dir.exists() || !dir.isDirectory()) {
            return children;
        }
        collectFiles(baseDir, dir, dir, extension, children);
        children.sort((a, b) -> a.get("relativePath").compareTo(b.get("relativePath")));
        return children;
    }

    private void collectFiles(String baseDir, File root, File current, String extension, List<Map<String, String>> result) {
        File[] entries = current.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            if (entry.isDirectory()) {
                collectFiles(baseDir, root, entry, extension, result);
            } else if (extension.isEmpty() || entry.getName().endsWith(extension)) {
                String relative = root.toPath().relativize(entry.toPath()).toString().replace('\\', '/');
                Map<String, String> child = new LinkedHashMap<>();
                child.put("name", relative);
                child.put("relativePath", relative);
                child.put("fullPath", baseDir + "/" + relative);
                result.add(child);
            }
        }
    }

    private static String extractBaseDir(String globPath) {
        int starIndex = globPath.indexOf('*');
        if (starIndex <= 0) return globPath;
        String before = globPath.substring(0, starIndex);
        if (before.endsWith("/")) before = before.substring(0, before.length() - 1);
        return before;
    }

    private static String extractExtension(String globPath) {
        int dotIndex = globPath.lastIndexOf("*.");
        if (dotIndex < 0) return "";
        return globPath.substring(dotIndex + 1);
    }

    @SuppressWarnings("unchecked")
    private void flattenConfig(String moduleId, String prefix, Map<String, Object> values, List<Map<String, Object>> nodes) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String path = Texts.isBlank(prefix) ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = normalizeValue(entry.getValue());
            if (value instanceof Map<?, ?> map) {
                // 检查该节点是否注册为 dynamic_map 类型
                NodeMeta meta = resolveMeta(moduleId, path, "object");
                if ("dynamic_map".equals(meta.type())) {
                    // dynamic_map：不递归展开，把整个 map 作为可编辑值传给前端
                    nodes.add(node(moduleId, path, "object", value, true));
                } else {
                    nodes.add(node(moduleId, path, "object", Map.of(), false));
                    flattenConfig(moduleId, path, (Map<String, Object>) map, nodes);
                }
                continue;
            }
            nodes.add(node(moduleId, path, typeOf(value), value, true));
        }
    }

    private Map<String, Object> node(String moduleId, String path, String detectedType, Object value, boolean editable) {
        NodeMeta meta = resolveMeta(moduleId, path, detectedType);
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("path", path);
        node.put("label", meta.label());
        node.put("comment", meta.comment());
        node.put("type", Texts.isBlank(meta.type()) ? detectedType : meta.type());
        node.put("editable", editable);
        node.put("value", value);
        return node;
    }

    private NodeMeta resolveMeta(String moduleId, String path, String detectedType) {
        NodeMeta exact = NODE_META.get(key(moduleId, path));
        if (exact != null) {
            return exact;
        }
        NodeMeta common = NODE_META.get(key("*", path));
        if (common != null) {
            return common;
        }
        for (NodeMetaRule rule : NODE_RULES) {
            if (rule.matches(moduleId, path)) {
                return rule.toMeta();
            }
        }
        return fallbackMeta(moduleId, path, detectedType);
    }

    private NodeMeta fallbackMeta(String moduleId, String path, String detectedType) {
        String label = labelFor(path);
        String comment = switch (detectedType) {
            case "object" -> "配置分组节点，来自 " + moduleId + "/config.yml，用于归类其下方子配置。";
            case "boolean" -> "布尔开关配置，true 表示开启，false 表示关闭。";
            case "number" -> "数值配置，请按该字段语义填写整数或小数。";
            case "list" -> "列表配置，每一项会按顺序参与对应功能处理。";
            default -> "文本配置，来自 " + moduleId + "/config.yml。";
        };
        return new NodeMeta(label, comment, detectedType);
    }

    private String typeOf(Object value) {
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof List<?>) {
            return "list";
        }
        return "text";
    }

    private Object normalizeValue(Object value) {
        if (value instanceof YamlSection section) {
            return section.asMap();
        }
        return value;
    }

    private Object normalizeIncomingValue(Object current, Object value) {
        if (current instanceof Boolean) {
            return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
        }
        if (current instanceof Number) {
            return parseNumber(value);
        }
        if (current instanceof List<?>) {
            return value instanceof List<?> list ? list : Arrays.stream(String.valueOf(value).split("\\n"))
                    .map(String::trim)
                    .filter(entry -> !entry.isEmpty())
                    .toList();
        }
        return value == null ? "" : String.valueOf(value);
    }

    private Number parseNumber(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        String text = String.valueOf(value).trim();
        try {
            return text.contains(".") ? Double.parseDouble(text) : Integer.parseInt(text);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private File moduleFile(String moduleId, String relativePath) {
        return plugin.getDataFolder().toPath().getParent().resolve(moduleId).resolve(relativePath).toFile();
    }

    private static synchronized List<ModuleRegistration> registeredModules() {
        return List.copyOf(MODULES.values());
    }

    private static synchronized ModuleRegistration module(String moduleId) {
        return MODULES.get(moduleId);
    }

    private static FileRegistration primaryConfig(ModuleRegistration registration) {
        return registration.files().stream()
                .filter(file -> file.type() == WebConsoleFileType.CONFIG && file.structuredYaml())
                .findFirst()
                .orElse(null);
    }

    private static void registerDefaults() {
        defaultModule("EmakiCoreLib", "CoreLib 框架", "Web Console、Action、脚本与公共运行库", "core", "CoreLib 主配置");
        registerScriptFile("EmakiCoreLib", "CoreLib JS 脚本", "scripts/**/*.js", "CoreLib JavaScript 脚本目录，当前仅保留文本预览入口。");

        registerCommonComments();
        registerCoreLibComments();
    }

    private static void defaultModule(String id, String name, String summary, String tone, String configTitle) {
        registerModule(id, name, summary, tone);
        registerConfigFile(id, configTitle, "config.yml", "完整 config.yml 结构化配置注册。所有字段均通过 CoreLib 注释注册器补充说明。");
        registerCommonConfigComments(id);
    }

    private static void registerFile(String moduleId, String title, String relativePath, WebConsoleFileType type, String comment, boolean structuredYaml) {
        ModuleRegistration module = MODULES.get(moduleId);
        if (module == null) {
            registerModule(moduleId, moduleId, "外部插件注册的 Web Console 模块", "default");
            module = MODULES.get(moduleId);
        }
        FileRegistration next = new FileRegistration(title, relativePath, type, comment, structuredYaml);
        boolean exists = module.files().stream().anyMatch(file -> file.relativePath().equals(relativePath) && file.type() == type);
        if (!exists) {
            module.files().add(next);
        }
    }

    private static void registerCommonComments() {
        registerNodeComment("*", "version", "配置版本", "配置结构版本，通常不建议手动修改。", "text");
        registerNodeComment("*", "language", "语言", "语言文件 ID，对应 lang/<language>.yml。", "text");
        registerNodeComment("*", "release_default_data", "释放默认资源", "首次启动或缺失数据时写入默认资源。", "boolean");

        registerNodeKeyComment("*", "enabled", "启用", "控制该功能、条目或子系统是否启用。", "boolean");
        registerNodeKeyComment("*", "debug", "调试", "调试输出相关配置，生产环境通常建议关闭。", "object");
        registerNodeKeyComment("*", "permission", "权限", "权限与 OP 绕过等访问控制设置。", "object");
        registerNodeKeyComment("*", "gui", "GUI", "GUI 入口或模板相关配置，具体模板文件会注册为 GUI 文件类型。", "object");
        registerNodeKeyComment("*", "actions", "动作", "CoreLib Action 动作列表或动作分组，按顺序执行。", "object");
        registerNodeKeyComment("*", "success", "成功动作", "功能成功时触发的动作列表或成功分支配置。", "list");
        registerNodeKeyComment("*", "failure", "失败动作", "功能失败时触发的动作列表或失败分支配置。", "list");
        registerNodeKeyComment("*", "item_sources", "物品来源", "支持 minecraft、CraftEngine、ItemsAdder、Nexo、MMOItems 等来源格式的物品 ID 列表。", "list");
        registerNodeKeyComment("*", "material", "材料", "Minecraft 原版材料或物品来源 ID。", "text");
        registerNodeKeyComment("*", "amount", "数量", "消耗、产出或显示用的数量。", "number");
        registerNodeKeyComment("*", "title", "标题", "GUI、消息或显示标题文本。", "text");
        registerNodeKeyComment("*", "size", "大小", "GUI 行数、槽位数或集合容量等数值。", "number");
        registerNodeKeyComment("*", "slots", "槽位", "GUI 槽位列表或槽位规则。", "list");
        registerNodeKeyComment("*", "items", "物品", "物品配置分组；物品定义文件会统一标记为 ITEM 类型。", "object");
        registerNodeKeyComment("*", "commands", "命令", "命令列表，通常按顺序执行。", "list");
        registerNodeKeyComment("*", "chance", "概率", "成功率、触发率或权重概率，按该字段上下文解释。", "number");
        registerNodeSuffixComment("*", ".enabled", "启用", "控制该子项是否启用。", "boolean");
        registerNodeSuffixComment("*", ".default_chance", "默认概率", "未单独配置时使用的默认概率。", "number");
        registerNodeSuffixComment("*", ".op_bypass", "OP 绕过", "开启后 OP 可跳过对应消耗、权限或条件检查。", "boolean");
        registerNodeContainsComment("*", ".actions.", "动作配置", "CoreLib Action 动作配置，支持模板、延迟和内联变量。", "list");
    }

    private static void registerCoreLibComments() {
        registerNodeComment("EmakiCoreLib", "web_console", "Web Console", "内置前端控制台开放策略与鉴权配置。", "object");
        registerNodeComment("EmakiCoreLib", "web_console.enabled", "启用前端", "开启后监听 host:port，reload 会先关闭再按新配置启动。", "boolean");
        registerNodeComment("EmakiCoreLib", "web_console.host", "监听地址", "127.0.0.1 仅本机，0.0.0.0 表示所有网卡。", "text");
        registerNodeComment("EmakiCoreLib", "web_console.port", "监听端口", "Web Console HTTP 端口。", "number");
        registerNodeComment("EmakiCoreLib", "web_console.public_access_warning", "公网提示", "当监听地址可能对外开放时，在登录响应中提示风险。", "boolean");
        registerNodeComment("EmakiCoreLib", "web_console.auth", "登录鉴权", "Web Console 登录账号、密码和会话有效期。", "object");
        registerNodeComment("EmakiCoreLib", "web_console.auth.username", "账号", "Web Console 登录账号。", "text");
        registerNodeComment("EmakiCoreLib", "web_console.auth.password", "密码", "Web Console 登录密码，启用前必须修改默认值。", "text");
        registerNodeComment("EmakiCoreLib", "web_console.auth.session_timeout_minutes", "会话分钟", "登录 Token 的有效分钟数。", "number");
        registerNodeComment("EmakiCoreLib", "web_console.security", "安全限制", "Web Console 请求体、写入权限等安全限制。", "object");
        registerNodeComment("EmakiCoreLib", "web_console.security.allow_config_write", "允许写配置", "开启后 Web Console 才允许保存配置变更。", "boolean");
        registerNodeComment("EmakiCoreLib", "web_console.security.max_request_body_kb", "请求体上限", "单次 Web 请求体大小上限，单位 KB。", "number");
        registerNodeComment("EmakiCoreLib", "action", "Action", "CoreLib 动作系统配置。", "object");
        registerNodeComment("EmakiCoreLib", "action.templates", "动作模板", "可在配方或动作列表中通过 @template=名称 引用的动作模板。每个子键为模板名称，值为动作列表。", "dynamic_map");
        registerNodeComment("EmakiCoreLib", "script", "CoreLib JS", "CoreLib JavaScript 引擎与脚本安全配置。", "object");
        registerNodeComment("EmakiCoreLib", "script.enabled", "启用脚本", "是否启用 CoreLib JavaScript 动作能力。", "boolean");
        registerNodeComment("EmakiCoreLib", "script.engine", "脚本引擎", "GraalJS 引擎、超时、缓存和宿主访问配置。", "object");
        registerNodeComment("EmakiCoreLib", "script.security", "脚本安全", "脚本路径、动作派发和调用深度限制。", "object");
    }


    private static String fileId(String moduleId, FileRegistration registration) {
        return (moduleId + "-" + registration.type().name() + "-" + registration.relativePath())
                .toLowerCase()
                .replace("**/", "")
                .replace("*", "all")
                .replace('/', '-')
                .replace('\\', '-')
                .replace('.', '-');
    }

    private static String labelFor(String path) {
        String key = lastKey(path);
        return switch (key) {
            case "enabled" -> "启用";
            case "language" -> "语言";
            case "version" -> "配置版本";
            case "host" -> "监听地址";
            case "port" -> "监听端口";
            case "username" -> "账号";
            case "password" -> "密码";
            default -> key.replace('_', ' ');
        };
    }

    private static String lastKey(String path) {
        return path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
    }

    private static String key(String moduleId, String path) {
        return moduleId + ":" + path;
    }

    private static String icon(String tone) {
        String d = switch (tone) {
            // CoreLib: 齿轮+代码括号，代表核心框架引擎
            case "core" -> "M19 4l1.5 3.5L24 9l-3.5 1.5L19 14l-1.5-3.5L14 9l3.5-1.5L19 4zM19 14v4m-6 2h12a2 2 0 012 2v8a2 2 0 01-2 2H13a2 2 0 01-2 2v-8a2 2 0 012-2zm2 4h3m-3 3h5";
            // Attribute: 六边形雷达图+数值线，代表属性面板
            case "attribute" -> "M19 5l10 6v12l-10 6-10-6V11l10-6zm0 6v12m-8.5-9H27.5M12 14l7 9m0-9l7 9";
            // Cooking: 炒锅+蒸汽+火焰，代表烹饪工位
            case "cooking" -> "M8 22c0-5 4-8 11-8s11 3 11 8M8 22c0 4 5 7 11 7s11-3 11-7M12 14c1-2 0-4 1.5-6m5.5 6c1-2 0-4 1.5-6m5.5 6c1-2 0-4 1.5-6M6 22h26";
            // Forge: 铁砧+锤子，代表锻造
            case "forge" -> "M10 28h18M13 28v-4h12v4M16 24v-3a3 3 0 013-3h0a3 3 0 013 3v3M25 7l4 4-8 8-4-4 8-8zM25 7l2-2m-2 6l-2 2";
            // Gem: 切割宝石多面体，代表宝石镶嵌
            case "gem" -> "M9 14l10-8 10 8-10 18L9 14zm10-8l0 26M9 14h20M13 10l6 22m0-22l6 22";
            // Item: 剑+盾牌轮廓，代表装备物品
            case "item" -> "M24 5l-9 9m4 4l-9 9M15 14l-2 2m11-5a7 7 0 11-7 7M8 26l3 3m-1-5l5 5M27 9l2 2";
            // Skills: 手掌+能量环，代表技能释放
            case "skills" -> "M19 32v-6m-5.5-2.5L11 26m13.5-2.5L27 26M19 8a8 8 0 110 16 8 8 0 010-16zm0 3v5l3 3m-3-12V2m8 4l-2 2M9 6l2 2";
            // Strengthen: 向上箭头+星星，代表强化升级
            case "strengthen" -> "M19 30V12m-6 6l6-6 6 6M12 8l1.5 3 3.5.5-2.5 2.5.5 3.5-3-1.5L9 17.5l.5-3.5L7 11.5l3.5-.5L12 8zm14 0l1.5 3 3.5.5-2.5 2.5.5 3.5-3-1.5-3 1.5.5-3.5-2.5-2.5 3.5-.5L26 8z";
            default -> "M7 7h24v24H7z";
        };
        return "<svg viewBox='0 0 38 38' xmlns='http://www.w3.org/2000/svg'><path d='" + d + "' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'/></svg>";
    }

    public enum WebConsoleFileType {
        CONFIG,
        GUI,
        ITEM,
        SCRIPT
    }

    private enum MatchType {
        SUFFIX,
        CONTAINS,
        KEY
    }

    private record ModuleRegistration(String id, String name, String summary, String tone, List<FileRegistration> files) {}
    private record FileRegistration(String title, String relativePath, WebConsoleFileType type, String comment, boolean structuredYaml) {}
    private record NodeMeta(String label, String comment, String type) {}

    private record NodeMetaRule(String moduleId, MatchType matchType, String pattern, String label, String comment, String type) {
        private boolean matches(String currentModuleId, String path) {
            if (!"*".equals(moduleId) && !moduleId.equals(currentModuleId)) {
                return false;
            }
            return switch (matchType) {
                case SUFFIX -> path.endsWith(pattern);
                case CONTAINS -> path.contains(pattern);
                case KEY -> lastKey(path).equals(pattern);
            };
        }

        private NodeMeta toMeta() {
            return new NodeMeta(label, comment, type);
        }
    }
}
