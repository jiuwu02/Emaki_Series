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

    private static final String DEFAULT_ICON_SVG = svgPath("M8 8h22v22H8zM13 13h12v12H13z");

    private static final Map<String, ModuleRegistration> MODULES = new LinkedHashMap<>();
    private static final Map<String, EditorRegistration> EDITORS = new LinkedHashMap<>();
    private static final Map<String, WebExtensionRegistration> EXTENSIONS = new LinkedHashMap<>();
    private static final Map<String, NodeMeta> NODE_META = new LinkedHashMap<>();
    private static final List<NodeMetaRule> NODE_RULES = new ArrayList<>();

    static {
        registerCommonComments();
    }

    private final JavaPlugin plugin;

    public WebConsoleRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 注册一个 Web Console 模块入口。其他插件只需要在启用阶段调用一次，后续文件和字段注释都挂到这个模块下。
     */
    public static synchronized void registerModule(String id, String name, String summary, String tone) {
        registerModule(id, name, summary, tone, DEFAULT_ICON_SVG);
    }

    /**
     * 注册一个带插件自有 SVG 的 Web Console 模块入口。SVG 必须由模块所有者提供，CoreLib 不内置外部插件图标。
     */
    public static synchronized void registerModule(String id, String name, String summary, String tone, String iconSvg) {
        ModuleRegistration existing = MODULES.get(id);
        List<FileRegistration> files = existing == null ? new ArrayList<>() : existing.files();
        MODULES.put(id, new ModuleRegistration(id, name, summary, tone, normalizeIcon(iconSvg), files));
    }

    public static synchronized void registerModule(JavaPlugin plugin, String name, String summary, String tone) {
        registerModule(plugin.getName(), name, summary, tone);
    }

    public static synchronized void registerModule(JavaPlugin plugin, String name, String summary, String tone, String iconSvg) {
        registerModule(plugin.getName(), name, summary, tone, iconSvg);
    }

    /**
     * 插件关闭时主动反注册，避免 reload 后残留已卸载插件的 Web Console 文件入口。
     */
    public static synchronized void unregisterModule(JavaPlugin plugin) {
        String moduleId = plugin.getName();
        MODULES.remove(moduleId);
        EDITORS.values().removeIf(editor -> moduleId.equals(editor.moduleId()));
        EXTENSIONS.values().removeIf(extension -> moduleId.equals(extension.moduleId()));
        NODE_META.keySet().removeIf(key -> key.startsWith(moduleId + ":"));
        NODE_RULES.removeIf(rule -> moduleId.equals(rule.moduleId()));
    }

    /**
     * 注册普通配置文件。CONFIG 是默认文件类型，前端后续可按类型渲染不同编辑体验。
     */
    public static synchronized void registerConfigFile(String moduleId, String title, String relativePath, String comment) {
        boolean structured = !relativePath.contains("*");
        registerFile(moduleId, title, relativePath, WebConsoleFileType.CONFIG, comment, structured);
    }

    public static synchronized void registerConfigFile(JavaPlugin plugin, String title, String relativePath, String comment) {
        if (plugin == null) {
            return;
        }
        registerConfigFile(plugin.getName(), title, relativePath, comment);
    }

    /**
     * 注册 GUI 模板文件或模板目录。暂时只统一类型，专属前端显示后续再接入。
     */
    public static synchronized void registerGuiFile(String moduleId, String title, String relativePath, String comment) {
        registerFile(moduleId, title, relativePath, WebConsoleFileType.GUI, comment, false);
    }

    public static synchronized void registerGuiFile(JavaPlugin plugin, String title, String relativePath, String comment) {
        if (plugin == null) {
            return;
        }
        registerGuiFile(plugin.getName(), title, relativePath, comment);
    }

    /**
     * 注册物品定义文件或物品目录。未指定 editorId 时前端使用 CoreLib 的通用 ITEM 编辑器。
     */
    public static synchronized void registerItemFile(String moduleId, String title, String relativePath, String comment) {
        registerItemFile(moduleId, title, relativePath, comment, "");
    }

    public static synchronized void registerItemFile(JavaPlugin plugin, String title, String relativePath, String comment) {
        if (plugin == null) {
            return;
        }
        registerItemFile(plugin.getName(), title, relativePath, comment);
    }

    /**
     * 注册带专属编辑器的物品定义文件。editorId 由子插件命名，例如 emakigem:gem。
     */
    public static synchronized void registerItemFile(String moduleId, String title, String relativePath, String comment, String editorId) {
        registerFile(moduleId, title, relativePath, WebConsoleFileType.ITEM, comment, false, editorId);
    }

    public static synchronized void registerItemFile(JavaPlugin plugin, String title, String relativePath, String comment, String editorId) {
        if (plugin == null) {
            return;
        }
        registerItemFile(plugin.getName(), title, relativePath, comment, editorId);
    }

    public static synchronized void registerEditorDescriptor(String moduleId, String editorId, Map<String, Object> descriptor) {
        if (Texts.isBlank(moduleId) || Texts.isBlank(editorId) || descriptor == null) {
            return;
        }
        Map<String, Object> copy = new LinkedHashMap<>(descriptor);
        copy.putIfAbsent("id", editorId);
        copy.putIfAbsent("moduleId", moduleId);
        EDITORS.put(editorId, new EditorRegistration(moduleId, editorId, copy));
    }

    public static synchronized void registerEditorDescriptor(JavaPlugin plugin, String editorId, Map<String, Object> descriptor) {
        if (plugin == null) {
            return;
        }
        registerEditorDescriptor(plugin.getName(), editorId, descriptor);
    }

    /**
     * 注册一个前端扩展脚本。resourcePath 是插件 jar 内的资源路径，例如 web-extensions/emakigem-item-surface.js。
     * 脚本会在 Web Console 获取 registry 后动态加载，并通过 window.EmakiWebConsole.registerSurface 注册页面。
     */
    public static synchronized void registerWebExtension(String moduleId, String id, String resourcePath) {
        if (Texts.isBlank(moduleId) || Texts.isBlank(id) || Texts.isBlank(resourcePath)) {
            return;
        }
        String safePath = resourcePath.replace('\\', '/');
        if (safePath.startsWith("/") || safePath.contains("..")) {
            return;
        }
        EXTENSIONS.put(id, new WebExtensionRegistration(moduleId, id, safePath));
    }

    public static synchronized void registerWebExtension(JavaPlugin plugin, String id, String resourcePath) {
        if (plugin == null) {
            return;
        }
        registerWebExtension(plugin.getName(), id, resourcePath);
    }

    public static synchronized void registerScriptFile(String moduleId, String title, String relativePath, String comment) {
        registerFile(moduleId, title, relativePath, WebConsoleFileType.SCRIPT, comment, false);
    }

    public static synchronized void registerScriptFile(JavaPlugin plugin, String title, String relativePath, String comment) {
        if (plugin == null) {
            return;
        }
        registerScriptFile(plugin.getName(), title, relativePath, comment);
    }

    public static synchronized void registerNodeComment(String moduleId, String path, String label, String comment, String type) {
        NODE_META.put(key(moduleId, path), new NodeMeta(label, comment, type));
    }

    public static synchronized void registerNodeComment(JavaPlugin plugin, String path, String label, String comment, String type) {
        if (plugin == null) {
            return;
        }
        registerNodeComment(plugin.getName(), path, label, comment, type);
    }

    public static synchronized void registerCommonConfigComments(String moduleId) {
        registerNodeComment(moduleId, "version", "配置版本", "配置结构版本，通常不建议手动修改。", "text");
        registerNodeComment(moduleId, "language", "语言", "语言文件 ID，对应 lang/<language>.yml。", "text");
        registerNodeComment(moduleId, "release_default_data", "释放默认资源", "首次启动或缺失数据时写入默认资源。", "boolean");
    }

    public static synchronized void registerCommonConfigComments(JavaPlugin plugin) {
        if (plugin == null) {
            return;
        }
        registerCommonConfigComments(plugin.getName());
    }

    public static synchronized void registerNodeSuffixComment(String moduleId, String suffix, String label, String comment, String type) {
        NODE_RULES.add(new NodeMetaRule(moduleId, MatchType.SUFFIX, suffix, label, comment, type));
    }

    public static synchronized void registerNodeSuffixComment(JavaPlugin plugin, String suffix, String label, String comment, String type) {
        if (plugin == null) {
            return;
        }
        registerNodeSuffixComment(plugin.getName(), suffix, label, comment, type);
    }

    public static synchronized void registerNodeContainsComment(String moduleId, String fragment, String label, String comment, String type) {
        NODE_RULES.add(new NodeMetaRule(moduleId, MatchType.CONTAINS, fragment, label, comment, type));
    }

    public static synchronized void registerNodeContainsComment(JavaPlugin plugin, String fragment, String label, String comment, String type) {
        if (plugin == null) {
            return;
        }
        registerNodeContainsComment(plugin.getName(), fragment, label, comment, type);
    }

    public static synchronized void registerNodeKeyComment(String moduleId, String keyName, String label, String comment, String type) {
        NODE_RULES.add(new NodeMetaRule(moduleId, MatchType.KEY, keyName, label, comment, type));
    }

    public static synchronized void registerNodeKeyComment(JavaPlugin plugin, String keyName, String label, String comment, String type) {
        if (plugin == null) {
            return;
        }
        registerNodeKeyComment(plugin.getName(), keyName, label, comment, type);
    }

    /**
     * 按需加载单个子文件的结构化 YAML 节点列表。
     * 用于 glob 路径注册的 CONFIG 文件，前端点击子文件时调用。
     */
    public Map<String, Object> fileNodes(String moduleId, String relativePath) throws IOException {
        if (Texts.isBlank(moduleId) || Texts.isBlank(relativePath)) {
            throw new IOException("缺少 moduleId 或 path 参数");
        }
        File file = moduleFile(moduleId, relativePath);
        if (!file.exists() || !file.isFile()) {
            throw new IOException("文件不存在: " + relativePath);
        }
        YamlSection yaml = YamlFiles.load(file);
        List<Map<String, Object>> nodes = new ArrayList<>();
        flattenConfig(moduleId, "", yaml.asMap(), nodes);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("moduleId", moduleId);
        result.put("path", relativePath);
        result.put("nodes", nodes);
        return result;
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
            module.put("icon", registration.iconSvg());
            module.put("present", true);
            module.put("enabled", true);
            module.put("version", installed == null ? "" : installed.getDescription().getVersion());
            module.put("files", files);
            modules.add(module);

            tree.add(moduleTreeNode(registration, files));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modules", modules);
        result.put("tree", tree);
        result.put("editors", editorDescriptors());
        result.put("extensions", webExtensions());
        return result;
    }

    public void saveValue(String moduleId, String path, Object value) throws IOException {
        saveValue(moduleId, null, path, value);
    }

    public void saveValue(String moduleId, String filePath, String path, Object value) throws IOException {
        ModuleRegistration registration = module(moduleId);
        if (registration == null) {
            throw new IOException("模块未注册");
        }
        File file;
        if (!Texts.isBlank(filePath)) {
            FileRegistration config = configByPath(registration, filePath);
            if (config != null) {
                file = moduleFile(moduleId, config.relativePath());
            } else {
                // 支持 glob 子文件的直接路径保存
                file = moduleFile(moduleId, filePath);
                if (!file.exists() || !file.isFile()) {
                    throw new IOException("文件不存在: " + filePath);
                }
            }
        } else {
            FileRegistration config = primaryConfig(registration);
            if (config == null) {
                throw new IOException("模块未注册可写配置文件");
            }
            file = moduleFile(moduleId, config.relativePath());
        }
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
        if (Texts.isNotBlank(registration.editorId())) {
            entry.put("editorId", registration.editorId());
        }
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

    private Map<String, Object> moduleTreeNode(ModuleRegistration registration, List<Map<String, Object>> files) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", registration.id());
        node.put("label", registration.name());
        node.put("type", "module");
        node.put("moduleId", registration.id());
        node.put("icon", registration.iconSvg());
        node.put("tone", registration.tone());
        node.put("children", files.stream().map(this::fileTreeNode).toList());
        return node;
    }

    private Map<String, Object> fileTreeNode(Map<String, Object> file) {
        Map<String, Object> node = new LinkedHashMap<>();
        String fileId = stringValue(file.get("id"));
        String moduleId = stringValue(file.get("moduleId"));
        String kind = stringValue(file.get("kind"));
        node.put("id", fileId);
        node.put("label", stringValue(file.get("title")));
        node.put("type", "file");
        node.put("moduleId", moduleId);
        node.put("fileId", fileId);
        node.put("kind", kind);
        node.put("path", stringValue(file.get("path")));
        node.put("comment", stringValue(file.get("comment")));
        Object childrenValue = file.get("children");
        if (childrenValue instanceof List<?> children && !children.isEmpty()) {
            List<Map<String, Object>> childNodes = new ArrayList<>();
            for (Object childValue : children) {
                if (childValue instanceof Map<?, ?> child) {
                    childNodes.add(childTreeNode(file, child));
                }
            }
            node.put("children", childNodes);
        }
        return node;
    }

    private Map<String, Object> childTreeNode(Map<String, Object> file, Map<?, ?> child) {
        String fileId = stringValue(file.get("id"));
        String moduleId = stringValue(file.get("moduleId"));
        String kind = stringValue(file.get("kind"));
        String relativePath = stringValue(child.get("relativePath"));
        String fullPath = stringValue(child.get("fullPath"));
        String childPath = "SCRIPT".equalsIgnoreCase(kind) || Texts.isBlank(fullPath) ? relativePath : fullPath;
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", fileId + ":" + childPath);
        node.put("label", stringValue(child.get("name")));
        node.put("type", "child");
        node.put("moduleId", moduleId);
        node.put("fileId", fileId);
        node.put("kind", kind);
        node.put("path", childPath);
        node.put("childPath", childPath);
        return node;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
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
        String resolvedType = Texts.isBlank(meta.type()) ? detectedType : meta.type();
        // 支持 enum:OPT1,OPT2,OPT3 格式（静态枚举）
        if (resolvedType.startsWith("enum:")) {
            node.put("type", "enum");
            String[] options = resolvedType.substring(5).split(",");
            node.put("options", java.util.Arrays.asList(options));
        }
        // 支持 dynamic_enum:目录路径 格式（动态枚举，扫描目录下 YAML 文件的 id 字段）
        else if (resolvedType.startsWith("dynamic_enum:")) {
            node.put("type", "enum");
            String dirPath = resolvedType.substring("dynamic_enum:".length());
            node.put("options", scanDynamicEnumOptions(moduleId, dirPath));
        } else {
            node.put("type", resolvedType);
        }
        node.put("editable", editable);
        node.put("value", value);
        return node;
    }

    private List<String> scanDynamicEnumOptions(String moduleId, String dirPath) {
        List<String> options = new ArrayList<>();
        File dir = moduleFile(moduleId, dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return options;
        }
        collectYamlIds(dir, options);
        options.sort(String::compareTo);
        return options;
    }

    private void collectYamlIds(File dir, List<String> result) {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            if (entry.isDirectory()) {
                collectYamlIds(entry, result);
            } else if (entry.getName().endsWith(".yml") || entry.getName().endsWith(".yaml")) {
                try {
                    YamlSection yaml = YamlFiles.load(entry);
                    String id = yaml.getString("id", null);
                    if (id != null && !id.isBlank()) {
                        result.add(id);
                    } else {
                        // 没有 id 字段时，使用文件名（去掉扩展名）
                        String name = entry.getName();
                        result.add(name.substring(0, name.lastIndexOf('.')));
                    }
                } catch (Exception ignored) {
                    // 解析失败时使用文件名
                    String name = entry.getName();
                    result.add(name.substring(0, name.lastIndexOf('.')));
                }
            }
        }
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

    public static synchronized List<String> registeredModuleIds() {
        return List.copyOf(MODULES.keySet());
    }

    public static synchronized boolean isModuleRegistered(String moduleId) {
        return MODULES.containsKey(moduleId);
    }

    private static synchronized List<ModuleRegistration> registeredModules() {
        return List.copyOf(MODULES.values());
    }

    private static synchronized ModuleRegistration module(String moduleId) {
        return MODULES.get(moduleId);
    }

    private static synchronized Map<String, Object> editorDescriptors() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (EditorRegistration editor : EDITORS.values()) {
            result.put(editor.editorId(), new LinkedHashMap<>(editor.descriptor()));
        }
        return result;
    }

    private static synchronized List<Map<String, Object>> webExtensions() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (WebExtensionRegistration extension : EXTENSIONS.values()) {
            Plugin installed = Bukkit.getPluginManager().getPlugin(extension.moduleId());
            if (installed == null || !installed.isEnabled()) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("moduleId", extension.moduleId());
            entry.put("id", extension.id());
            entry.put("url", "/extensions/" + extension.moduleId() + "/" + extension.resourcePath());
            result.add(entry);
        }
        return result;
    }

    public static synchronized String registeredExtensionResourcePath(String moduleId, String resourcePath) {
        for (WebExtensionRegistration extension : EXTENSIONS.values()) {
            if (extension.moduleId().equals(moduleId) && extension.resourcePath().equals(resourcePath)) {
                return extension.resourcePath();
            }
        }
        return null;
    }

    private static FileRegistration primaryConfig(ModuleRegistration registration) {
        return registration.files().stream()
                .filter(file -> file.type() == WebConsoleFileType.CONFIG && file.structuredYaml())
                .findFirst()
                .orElse(null);
    }

    private static FileRegistration configByPath(ModuleRegistration registration, String relativePath) {
        return registration.files().stream()
                .filter(file -> file.type() == WebConsoleFileType.CONFIG && file.structuredYaml() && file.relativePath().equals(relativePath))
                .findFirst()
                .orElse(primaryConfig(registration));
    }

    private static void registerFile(String moduleId, String title, String relativePath, WebConsoleFileType type, String comment, boolean structuredYaml) {
        registerFile(moduleId, title, relativePath, type, comment, structuredYaml, "");
    }

    private static void registerFile(String moduleId, String title, String relativePath, WebConsoleFileType type, String comment, boolean structuredYaml, String editorId) {
        ModuleRegistration module = MODULES.get(moduleId);
        if (module == null) {
            registerModule(moduleId, moduleId, "外部插件注册的 Web Console 模块", "default");
            module = MODULES.get(moduleId);
        }
        FileRegistration next = new FileRegistration(title, relativePath, type, comment, structuredYaml, Texts.toStringSafe(editorId));
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

    private static String normalizeIcon(String iconSvg) {
        return Texts.isBlank(iconSvg) ? DEFAULT_ICON_SVG : iconSvg;
    }

    private static String svgPath(String d) {
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

    private record ModuleRegistration(String id, String name, String summary, String tone, String iconSvg, List<FileRegistration> files) {}
    private record EditorRegistration(String moduleId, String editorId, Map<String, Object> descriptor) {}
    private record WebExtensionRegistration(String moduleId, String id, String resourcePath) {}
    private record FileRegistration(String title, String relativePath, WebConsoleFileType type, String comment, boolean structuredYaml, String editorId) {}
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
