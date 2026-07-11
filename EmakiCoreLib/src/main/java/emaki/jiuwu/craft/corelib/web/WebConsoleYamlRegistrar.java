package emaki.jiuwu.craft.corelib.web;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public final class WebConsoleYamlRegistrar {

    private static final String RESOURCE_NAME = "web-console.yml";
    private static final Map<String, Plugin> SCANNED = new ConcurrentHashMap<>();

    private WebConsoleYamlRegistrar() {}

    static void unmarkScanned(String moduleId) {
        if (Texts.isNotBlank(moduleId)) {
            SCANNED.remove(moduleId);
        }
    }

    public static synchronized void scanAll() {
        clearUnavailableScans();
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (!plugin.isEnabled()) continue;
            if (!(plugin instanceof JavaPlugin javaPlugin)) continue;
            scanPlugin(javaPlugin);
        }
    }

    public static synchronized void scanPlugin(JavaPlugin plugin) {
        if (plugin == null || !plugin.isEnabled()) return;
        String moduleId = plugin.getName();
        Plugin scanned = SCANNED.get(moduleId);
        if (scanned == plugin) return;
        if (scanned != null) {
            WebConsoleRegistry.unregisterModule(moduleId);
        }
        SCANNED.put(moduleId, plugin);

        if (WebConsoleRegistry.isModuleRegistered(moduleId)) return;

        try (InputStream input = plugin.getResource(RESOURCE_NAME)) {
            if (input == null) return;
            String content = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            YamlSection yaml = YamlFiles.load(content);
            applyRegistration(plugin, moduleId, yaml);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[WebConsole] Failed to parse web-console.yml for " + moduleId, e);
        }
    }

    private static void clearUnavailableScans() {
        for (Map.Entry<String, Plugin> entry : List.copyOf(SCANNED.entrySet())) {
            Plugin installed = Bukkit.getPluginManager().getPlugin(entry.getKey());
            if (installed != entry.getValue() || installed == null || !installed.isEnabled()) {
                SCANNED.remove(entry.getKey(), entry.getValue());
                WebConsoleRegistry.unregisterModule(entry.getKey());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void applyRegistration(JavaPlugin plugin, String moduleId, YamlSection yaml) {
        YamlSection moduleSection = yaml.getSection("module");
        if (moduleSection != null) {
            String name = defaultString(moduleSection.getString("name", ""), moduleId);
            String summary = moduleSection.getString("summary", "");
            String tone = moduleSection.getString("tone", "default");
            String icon = moduleSection.getString("icon", "");
            if (Texts.isNotBlank(icon)) {
                WebConsoleRegistry.registerModule(moduleId, name, summary, tone, icon);
            } else {
                WebConsoleRegistry.registerModule(moduleId, name, summary, tone);
            }
        }

        List<?> files = yaml.getList("files");
        if (files != null) {
            for (Object entry : files) {
                if (!(entry instanceof Map<?, ?> fileMap)) continue;
                String path = stringVal(fileMap.get("path"));
                String title = defaultString(stringVal(fileMap.get("title")), path);
                String kind = stringVal(fileMap.get("kind")).toUpperCase(java.util.Locale.ROOT);
                String comment = stringVal(fileMap.get("comment"));
                String editor = stringVal(fileMap.get("editor"));
                if (Texts.isBlank(path)) continue;
                switch (kind) {
                    case "CONFIG" -> WebConsoleRegistry.registerConfigFile(moduleId, title, path, comment);
                    case "GUI" -> WebConsoleRegistry.registerGuiFile(moduleId, title, path, comment, editor);
                    case "ITEM" -> WebConsoleRegistry.registerItemFile(moduleId, title, path, comment, editor);
                    case "SCRIPT" -> WebConsoleRegistry.registerScriptFile(moduleId, title, path, comment);
                    default -> WebConsoleRegistry.registerResourceFile(moduleId, title, path, kind, comment, editor);
                }
            }
        }

        List<?> extensions = yaml.getList("extensions");
        if (extensions != null) {
            for (Object entry : extensions) {
                if (!(entry instanceof Map<?, ?> extMap)) continue;
                String id = stringVal(extMap.get("id"));
                String resource = stringVal(extMap.get("resource"));
                if (Texts.isNotBlank(id) && Texts.isNotBlank(resource)) {
                    WebConsoleRegistry.registerWebExtension(moduleId, id, resource);
                }
            }
        }

        List<?> nodes = yaml.getList("nodes");
        if (nodes != null) {
            registerNodes(moduleId, nodes);
        }

        YamlSection comments = yaml.getSection("comments");
        if (comments != null) {
            registerCommentsRecursive(moduleId, "", comments.asMap());
        }

        WebConsoleRegistry.registerCommonConfigComments(moduleId);
    }

    private static void registerNodes(String moduleId, List<?> nodes) {
        for (Object entry : nodes) {
            if (!(entry instanceof Map<?, ?> nodeMap)) continue;
            String path = stringVal(nodeMap.get("path"));
            String type = stringVal(nodeMap.get("type"));
            String label = stringVal(nodeMap.get("label"));
            String comment = stringVal(nodeMap.get("comment"));
            boolean creatableChildren = booleanVal(nodeMap.get("creatableChildren"));
            if (Texts.isBlank(path)) continue;
            if (creatableChildren) {
                WebConsoleRegistry.registerCreatableNode(moduleId, path, label, comment, type);
            } else {
                WebConsoleRegistry.registerNodeComment(moduleId, path, label, comment, type);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void registerCommentsRecursive(String moduleId, String prefix, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = entry.getValue();
            if (!(value instanceof Map<?, ?> nested)) continue;
            if (nested.containsKey("label")) {
                String label = stringVal(nested.get("label"));
                String comment = stringVal(nested.get("comment"));
                String type = stringVal(nested.get("type"));
                if (Texts.isNotBlank(label)) {
                    WebConsoleRegistry.registerNodeComment(moduleId, path, label, comment, type);
                }
            } else {
                registerCommentsRecursive(moduleId, path, (Map<String, Object>) nested);
            }
        }
    }

    private static String defaultString(String value, String fallback) {
        return Texts.isBlank(value) ? stringVal(fallback) : value;
    }

    private static boolean booleanVal(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(stringVal(value));
    }

    private static String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
