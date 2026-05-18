package emaki.jiuwu.craft.corelib.web;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

/**
 * 声明式 Web Console 注册器。
 * <p>
 * 扫描所有已启用插件的 {@code web-console.yml} 资源文件，自动调用
 * {@link WebConsoleRegistry} 的注册方法。已通过命令式 API 注册的模块不会被覆盖。
 * </p>
 */
public final class WebConsoleYamlRegistrar {

    private static final String RESOURCE_NAME = "web-console.yml";
    private static final Set<String> SCANNED = ConcurrentHashMap.newKeySet();

    private WebConsoleYamlRegistrar() {}

    static void unmarkScanned(String moduleId) {
        if (Texts.isNotBlank(moduleId)) {
            SCANNED.remove(moduleId);
        }
    }

    /**
     * 扫描所有已启用插件，对尚未通过命令式 API 注册的模块执行 YAML 声明式注册。
     * 此方法幂等，同一个插件只会被扫描一次。
     */
    public static synchronized void scanAll() {
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (!plugin.isEnabled()) continue;
            if (!(plugin instanceof JavaPlugin javaPlugin)) continue;
            scanPlugin(javaPlugin);
        }
    }

    /**
     * 扫描单个插件的 web-console.yml 资源。
     */
    public static synchronized void scanPlugin(JavaPlugin plugin) {
        String moduleId = plugin.getName();
        if (SCANNED.contains(moduleId)) return;
        SCANNED.add(moduleId);

        // 已通过命令式 API 注册的模块跳过
        if (WebConsoleRegistry.isModuleRegistered(moduleId)) return;

        try (InputStream input = plugin.getResource(RESOURCE_NAME)) {
            if (input == null) return;
            String content = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            YamlSection yaml = YamlFiles.load(content);
            applyRegistration(plugin, moduleId, yaml);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[WebConsole] 解析 web-console.yml 失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void applyRegistration(JavaPlugin plugin, String moduleId, YamlSection yaml) {
        // --- module ---
        YamlSection moduleSection = yaml.getSection("module");
        if (moduleSection != null) {
            String name = moduleSection.getString("name", moduleId);
            String summary = moduleSection.getString("summary", "");
            String tone = moduleSection.getString("tone", "default");
            String icon = moduleSection.getString("icon", "");
            if (Texts.isNotBlank(icon)) {
                WebConsoleRegistry.registerModule(moduleId, name, summary, tone, icon);
            } else {
                WebConsoleRegistry.registerModule(moduleId, name, summary, tone);
            }
        }

        // --- files ---
        List<?> files = yaml.getList("files");
        if (files != null) {
            for (Object entry : files) {
                if (!(entry instanceof Map<?, ?> fileMap)) continue;
                String title = stringVal(fileMap.get("title"));
                String path = stringVal(fileMap.get("path"));
                String kind = stringVal(fileMap.get("kind")).toUpperCase();
                String comment = stringVal(fileMap.get("comment"));
                String editor = stringVal(fileMap.get("editor"));
                if (Texts.isBlank(title) || Texts.isBlank(path)) continue;
                switch (kind) {
                    case "CONFIG" -> WebConsoleRegistry.registerConfigFile(moduleId, title, path, comment);
                    case "GUI" -> WebConsoleRegistry.registerGuiFile(moduleId, title, path, comment, editor);
                    case "ITEM" -> WebConsoleRegistry.registerItemFile(moduleId, title, path, comment, editor);
                    case "SCRIPT" -> WebConsoleRegistry.registerScriptFile(moduleId, title, path, comment);
                }
            }
        }

        // --- extensions ---
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

        // --- comments ---
        YamlSection comments = yaml.getSection("comments");
        if (comments != null) {
            registerCommentsRecursive(moduleId, "", comments.asMap());
        }

        // --- 自动注册通用配置注释 ---
        WebConsoleRegistry.registerCommonConfigComments(moduleId);
    }

    @SuppressWarnings("unchecked")
    private static void registerCommentsRecursive(String moduleId, String prefix, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = entry.getValue();
            if (!(value instanceof Map<?, ?> nested)) continue;
            // 如果包含 "label" 键，则视为一个 comment 定义节点
            if (nested.containsKey("label")) {
                String label = stringVal(nested.get("label"));
                String comment = stringVal(nested.get("comment"));
                String type = stringVal(nested.get("type"));
                if (Texts.isNotBlank(label)) {
                    WebConsoleRegistry.registerNodeComment(moduleId, path, label, comment, type);
                }
            } else {
                // 否则视为中间分组节点，递归处理
                registerCommentsRecursive(moduleId, path, (Map<String, Object>) nested);
            }
        }
    }

    private static String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
