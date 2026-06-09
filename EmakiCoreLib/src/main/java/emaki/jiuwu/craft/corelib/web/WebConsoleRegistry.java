package emaki.jiuwu.craft.corelib.web;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryType;
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
    private static final Map<String, List<Map<String, Object>>> CREATE_TEMPLATES = new LinkedHashMap<>();
    private static final Map<String, List<Map<String, Object>>> LIST_ITEM_FIELDS = new LinkedHashMap<>();
    private static final Map<String, String> UNIQUE_LIST_FIELDS = new LinkedHashMap<>();
    private static final List<NodeMetaRule> NODE_RULES = new ArrayList<>();

    static {
        registerCommonComments();
    }

    private final JavaPlugin plugin;

    public WebConsoleRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static void registerFromYaml(JavaPlugin plugin) {
        WebConsoleYamlRegistrar.scanPlugin(plugin);
    }

    public static synchronized void registerModule(String id, String name, String summary, String tone) {
        registerModule(id, name, summary, tone, DEFAULT_ICON_SVG);
    }

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

    public static synchronized void unregisterModule(JavaPlugin plugin) {
        String moduleId = plugin.getName();
        WebConsoleYamlRegistrar.unmarkScanned(moduleId);
        MODULES.remove(moduleId);
        EDITORS.values().removeIf(editor -> moduleId.equals(editor.moduleId()));
        EXTENSIONS.values().removeIf(extension -> moduleId.equals(extension.moduleId()));
        NODE_META.keySet().removeIf(key -> key.startsWith(moduleId + ":"));
        CREATE_TEMPLATES.keySet().removeIf(key -> key.startsWith(moduleId + ":"));
        LIST_ITEM_FIELDS.keySet().removeIf(key -> key.startsWith(moduleId + ":"));
        UNIQUE_LIST_FIELDS.keySet().removeIf(key -> key.startsWith(moduleId + ":"));
        NODE_RULES.removeIf(rule -> moduleId.equals(rule.moduleId()));
    }

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

    public static synchronized void registerGuiFile(String moduleId, String title, String relativePath, String comment) {
        registerGuiFile(moduleId, title, relativePath, comment, "");
    }

    public static synchronized void registerGuiFile(JavaPlugin plugin, String title, String relativePath, String comment) {
        if (plugin == null) {
            return;
        }
        registerGuiFile(plugin.getName(), title, relativePath, comment);
    }

    public static synchronized void registerGuiFile(String moduleId, String title, String relativePath, String comment, String editorId) {
        registerFile(moduleId, title, relativePath, WebConsoleFileType.GUI, comment, false, editorId);
    }

    public static synchronized void registerGuiFile(JavaPlugin plugin, String title, String relativePath, String comment, String editorId) {
        if (plugin == null) {
            return;
        }
        registerGuiFile(plugin.getName(), title, relativePath, comment, editorId);
    }

    public static synchronized void registerItemFile(String moduleId, String title, String relativePath, String comment) {
        registerItemFile(moduleId, title, relativePath, comment, "");
    }

    public static synchronized void registerItemFile(JavaPlugin plugin, String title, String relativePath, String comment) {
        if (plugin == null) {
            return;
        }
        registerItemFile(plugin.getName(), title, relativePath, comment);
    }

    public static synchronized void registerItemFile(String moduleId, String title, String relativePath, String comment, String editorId) {
        registerFile(moduleId, title, relativePath, WebConsoleFileType.ITEM, comment, false, editorId);
    }

    public static synchronized void registerItemFile(JavaPlugin plugin, String title, String relativePath, String comment, String editorId) {
        if (plugin == null) {
            return;
        }
        registerItemFile(plugin.getName(), title, relativePath, comment, editorId);
    }

    public static synchronized void registerResourceFile(String moduleId, String title, String relativePath, String kind, String comment, String editorId) {
        registerFile(moduleId, title, relativePath, WebConsoleFileType.resource(kind), comment, false, editorId);
    }

    public static synchronized void registerResourceFile(JavaPlugin plugin, String title, String relativePath, String kind, String comment, String editorId) {
        if (plugin == null) {
            return;
        }
        registerResourceFile(plugin.getName(), title, relativePath, kind, comment, editorId);
    }

    public static synchronized void registerEditorDescriptor(String moduleId, String editorId, Map<String, Object> descriptor) {
        if (Texts.isBlank(moduleId) || Texts.isBlank(editorId) || descriptor == null) {
            return;
        }
        EditorRegistration existing = EDITORS.get(editorId);
        Object existingFields = existing == null ? null : existing.descriptor().get("fields");
        Object incomingFields = descriptor.get("fields");
        Map<String, Object> copy = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing.descriptor());
        copy.putAll(descriptor);
        Map<String, Object> fields = mergeEditorFields(existingFields, incomingFields);
        if (!fields.isEmpty()) {
            copy.put("fields", fields);
        }
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

    @SafeVarargs
    public static Map<String, Object> editorSection(String title, Map<String, Object>... fields) {
        return editorSection(title, true, false, fields);
    }

    @SafeVarargs
    public static Map<String, Object> editorSection(String title, boolean collapsible, boolean defaultCollapsed, Map<String, Object>... fields) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("title", title);
        section.put("fields", List.of(fields));
        section.put("collapsible", collapsible);
        section.put("defaultCollapsed", defaultCollapsed);
        return section;
    }

    public static synchronized void registerGuiEditorDescriptor(String moduleId, String editorId, Map<String, Object> descriptor) {
        registerEditorDescriptor(moduleId, editorId, descriptor);
    }

    public static synchronized void registerGuiEditorDescriptor(JavaPlugin plugin, String editorId, Map<String, Object> descriptor) {
        registerEditorDescriptor(plugin, editorId, descriptor);
    }

    public static synchronized void registerEditorField(String moduleId, String editorId, String path, String label, String comment, String type) {
        if (Texts.isBlank(moduleId) || Texts.isBlank(editorId) || Texts.isBlank(path) || Texts.isBlank(label)) {
            return;
        }
        EditorRegistration existing = EDITORS.get(editorId);
        Map<String, Object> descriptor = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing.descriptor());
        Map<String, Object> fields = mergeEditorFields(descriptor.get("fields"), null);
        fields.put(path, editorField(path, label, comment, type));
        descriptor.putIfAbsent("id", editorId);
        descriptor.putIfAbsent("moduleId", moduleId);
        descriptor.put("fields", fields);
        EDITORS.put(editorId, new EditorRegistration(moduleId, editorId, descriptor));
    }

    public static synchronized void registerEditorField(JavaPlugin plugin, String editorId, String path, String label, String comment, String type) {
        if (plugin == null) {
            return;
        }
        registerEditorField(plugin.getName(), editorId, path, label, comment, type);
    }

    public static synchronized void registerGuiEditorField(String moduleId, String editorId, String path, String label, String comment, String type) {
        registerEditorField(moduleId, editorId, path, label, comment, type);
    }

    public static synchronized void registerGuiEditorField(JavaPlugin plugin, String editorId, String path, String label, String comment, String type) {
        registerEditorField(plugin, editorId, path, label, comment, type);
    }

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
        NODE_META.put(key(moduleId, path), new NodeMeta(label, comment, type, false));
    }

    public static synchronized void registerCreatableNode(String moduleId, String path, String label, String comment, String type) {
        NODE_META.put(key(moduleId, path), new NodeMeta(label, comment, type, true));
    }

    public static synchronized void registerCreatableNode(JavaPlugin plugin, String path, String label, String comment, String type) {
        if (plugin == null) {
            return;
        }
        registerCreatableNode(plugin.getName(), path, label, comment, type);
    }

    public static synchronized void registerNodeComment(JavaPlugin plugin, String path, String label, String comment, String type) {
        if (plugin == null) {
            return;
        }
        registerNodeComment(plugin.getName(), path, label, comment, type);
    }

    public static synchronized void registerCommonConfigComments(String moduleId) {
        registerNodeComment(moduleId, "version", "", "", "text");
        registerNodeComment(moduleId, "language", "", "", "text");
        registerNodeComment(moduleId, "release_default_data", "", "", "boolean");
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

    public static Map<String, Object> createTemplateField(String path, String label, String comment, String type, Object defaultValue) {
        return createTemplateField(path, label, comment, type, defaultValue, List.of(), "");
    }

    public static Map<String, Object> createTemplateField(String path, String label, String comment, String type, Object defaultValue, List<String> options, String optionLabelPrefix) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("path", path);
        field.put("label", label);
        field.put("comment", comment);
        field.put("type", type);
        field.put("defaultValue", defaultValue);
        if (options != null && !options.isEmpty()) {
            field.put("options", List.copyOf(options));
        }
        if (Texts.isNotBlank(optionLabelPrefix)) {
            field.put("optionLabelPrefix", optionLabelPrefix);
        }
        return field;
    }

    public static synchronized void registerCreateTemplate(String moduleId, String nodePath, String templateId, String label, List<Map<String, Object>> fields) {
        if (Texts.isBlank(moduleId) || Texts.isBlank(nodePath) || Texts.isBlank(templateId)) {
            return;
        }
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("id", templateId);
        template.put("label", Texts.isBlank(label) ? templateId : label);
        template.put("fields", fields == null ? List.of() : List.copyOf(fields));
        CREATE_TEMPLATES.computeIfAbsent(key(moduleId, nodePath), ignored -> new ArrayList<>()).add(template);
    }

    public static synchronized void registerCreateTemplate(JavaPlugin plugin, String nodePath, String templateId, String label, List<Map<String, Object>> fields) {
        if (plugin == null) {
            return;
        }
        registerCreateTemplate(plugin.getName(), nodePath, templateId, label, fields);
    }

    public static synchronized void registerListItemField(String moduleId, String listPath, Map<String, Object> field) {
        if (Texts.isBlank(moduleId) || Texts.isBlank(listPath) || field == null || Texts.isBlank(String.valueOf(field.get("path")))) {
            return;
        }
        LIST_ITEM_FIELDS.computeIfAbsent(key(moduleId, listPath), ignored -> new ArrayList<>()).add(new LinkedHashMap<>(field));
    }

    public static synchronized void registerListItemField(JavaPlugin plugin, String listPath, Map<String, Object> field) {
        if (plugin == null) {
            return;
        }
        registerListItemField(plugin.getName(), listPath, field);
    }

    public static synchronized void registerUniqueListField(String moduleId, String listPath, String fieldPath) {
        if (Texts.isBlank(moduleId) || Texts.isBlank(listPath) || Texts.isBlank(fieldPath)) {
            return;
        }
        UNIQUE_LIST_FIELDS.put(key(moduleId, listPath), fieldPath);
    }

    public static synchronized void registerUniqueListField(JavaPlugin plugin, String listPath, String fieldPath) {
        if (plugin == null) {
            return;
        }
        registerUniqueListField(plugin.getName(), listPath, fieldPath);
    }

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
        result.put("revision", fileRevision(file));
        result.put("nodes", nodes);
        return result;
    }

    public Map<String, Object> snapshot() {
        WebConsoleYamlRegistrar.scanAll();
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
        result.put("guiTypes", guiTypes());
        result.put("runtimeEnums", runtimeEnums());
        result.put("extensions", webExtensions());
        return result;
    }

    public long saveValue(String moduleId, String path, Object value) throws IOException {
        return saveValue(moduleId, null, path, value, null);
    }

    public long saveValue(String moduleId, String filePath, String path, Object value) throws IOException {
        return saveValue(moduleId, filePath, path, value, null);
    }

    public long saveValue(String moduleId, String filePath, String path, Object value, Long expectedRevision) throws IOException {
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
        long currentRevision = fileRevision(file);
        if (currentRevision != 0L && (expectedRevision == null || currentRevision != expectedRevision)) {
            throw new RevisionConflictException("文件已被其他管理员修改，请重载后再保存。", currentRevision);
        }
        YamlSection yaml = YamlFiles.load(file);
        Object current = yaml.get(path);
        if (current instanceof YamlSection || current instanceof Map<?, ?>) {
            throw new IOException("分组节点不能直接保存，请修改其子配置项");
        }
        yaml.set(path, normalizeIncomingValue(current, value));
        YamlFiles.save(file, yaml);
        return advanceFileRevision(file, currentRevision);
    }

    private Map<String, Object> fileSnapshot(String moduleId, FileRegistration registration) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", fileId(moduleId, registration));
        entry.put("moduleId", moduleId);
        entry.put("path", registration.relativePath());
        entry.put("revision", fileRegistrationRevision(moduleId, registration));
        entry.put("title", registration.title());
        entry.put("kind", registration.type().kind());
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
            List<Map<String, Object>> childNodes = childTreeNodes(file, children);
            if (!childNodes.isEmpty()) {
                node.put("children", childNodes);
            }
        }
        return node;
    }

    private List<Map<String, Object>> childTreeNodes(Map<String, Object> file, List<?> children) {
        List<Map<String, Object>> roots = new ArrayList<>();
        Map<String, Map<String, Object>> foldersByPath = new LinkedHashMap<>();
        for (Object childValue : children) {
            if (!(childValue instanceof Map<?, ?> child)) {
                continue;
            }
            addChildTreeNode(file, child, roots, foldersByPath);
        }
        return roots;
    }

    @SuppressWarnings("unchecked")
    private void addChildTreeNode(Map<String, Object> file, Map<?, ?> child, List<Map<String, Object>> roots, Map<String, Map<String, Object>> foldersByPath) {
        String fileId = stringValue(file.get("id"));
        String moduleId = stringValue(file.get("moduleId"));
        String kind = stringValue(file.get("kind"));
        String relativePath = normalizeTreePath(stringValue(child.get("relativePath")));
        String fullPath = normalizeTreePath(stringValue(child.get("fullPath")));
        String childPath = "SCRIPT".equalsIgnoreCase(kind) || Texts.isBlank(fullPath) ? relativePath : fullPath;
        String displayPath = Texts.isBlank(relativePath) ? childPath : relativePath;
        List<String> parts = Arrays.stream(displayPath.split("/"))
                .filter(Texts::isNotBlank)
                .toList();
        if (parts.isEmpty()) {
            return;
        }
        List<Map<String, Object>> siblings = roots;
        String folderDisplayPath = "";
        String folderChildPath = "";
        String childBasePrefix = basePrefix(childPath, displayPath);
        for (int index = 0; index < parts.size() - 1; index++) {
            String folderName = parts.get(index);
            folderDisplayPath = Texts.isBlank(folderDisplayPath) ? folderName : folderDisplayPath + "/" + folderName;
            folderChildPath = Texts.isBlank(childBasePrefix) ? folderDisplayPath : childBasePrefix + "/" + folderDisplayPath;
            String folderId = fileId + ":folder:" + folderChildPath;
            Map<String, Object> folder = foldersByPath.get(folderId);
            if (folder == null) {
                folder = folderTreeNode(moduleId, fileId, kind, folderName, folderChildPath);
                foldersByPath.put(folderId, folder);
                siblings.add(folder);
            }
            Object nested = folder.get("children");
            if (!(nested instanceof List<?>)) {
                nested = new ArrayList<Map<String, Object>>();
                folder.put("children", nested);
            }
            siblings = (List<Map<String, Object>>) nested;
        }
        siblings.add(childTreeNode(fileId, moduleId, kind, parts.get(parts.size() - 1), childPath));
    }

    private Map<String, Object> folderTreeNode(String moduleId, String fileId, String kind, String label, String path) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", fileId + ":folder:" + path);
        node.put("label", label);
        node.put("type", "folder");
        node.put("moduleId", moduleId);
        node.put("fileId", fileId);
        node.put("kind", kind);
        node.put("path", path);
        node.put("childPath", path);
        node.put("createPrefix", path);
        node.put("children", new ArrayList<Map<String, Object>>());
        return node;
    }

    private Map<String, Object> childTreeNode(String fileId, String moduleId, String kind, String label, String childPath) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", fileId + ":" + childPath);
        node.put("label", label);
        node.put("type", "child");
        node.put("moduleId", moduleId);
        node.put("fileId", fileId);
        node.put("kind", kind);
        node.put("path", childPath);
        node.put("childPath", childPath);
        return node;
    }

    private String basePrefix(String fullPath, String relativePath) {
        if (Texts.isBlank(fullPath) || Texts.isBlank(relativePath) || fullPath.equals(relativePath)) {
            return "";
        }
        return fullPath.endsWith("/" + relativePath) ? fullPath.substring(0, fullPath.length() - relativePath.length() - 1) : "";
    }

    private String normalizeTreePath(String path) {
        return stringValue(path).replace('\\', '/').replaceAll("^/+|/+$", "");
    }

    private long fileRegistrationRevision(String moduleId, FileRegistration registration) {
        String path = registration.relativePath();
        if (path.contains("*") || path.contains("?")) {
            return 0L;
        }
        return fileRevision(moduleFile(moduleId, path));
    }

    private long fileRevision(File file) {
        if (!file.exists()) return 0L;
        try {
            return Files.getLastModifiedTime(file.toPath()).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private long advanceFileRevision(File file, long previousRevision) throws IOException {
        long nextRevision = fileRevision(file);
        if (previousRevision > 0L && nextRevision <= previousRevision) {
            nextRevision = previousRevision + 1L;
            Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(nextRevision));
        }
        return nextRevision;
    }

    public static final class RevisionConflictException extends IOException {
        private final long currentRevision;

        public RevisionConflictException(String message, long currentRevision) {
            super(message);
            this.currentRevision = currentRevision;
        }

        public long currentRevision() {
            return currentRevision;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public FileCreationTarget creationTarget(String moduleId, String fileId) throws IOException {
        ModuleRegistration registration = module(moduleId);
        if (registration == null) {
            throw new IOException("模块未注册");
        }
        FileRegistration file = registration.files().stream()
                .filter(candidate -> fileId(moduleId, candidate).equals(fileId))
                .findFirst()
                .orElseThrow(() -> new IOException("文件入口不存在"));
        String globPath = file.relativePath();
        if (!globPath.contains("*")) {
            throw new IOException("此文件入口不支持新建文件");
        }
        return new FileCreationTarget(file.type(), extractBaseDir(globPath), extractExtension(globPath));
    }

    public record FileCreationTarget(WebConsoleFileType type, String baseDir, String extension) {}

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
                NodeMeta meta = resolveMeta(moduleId, path, "object");
                if ("dynamic_map".equals(meta.type())) {
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
        String resolvedType = resolveNodeType(detectedType, meta.type());
        if (resolvedType.startsWith("enum:")) {
            node.put("type", "enum");
            String[] options = resolvedType.substring(5).split(",");
            node.put("options", java.util.Arrays.asList(options));
        }
        else if (resolvedType.startsWith("dynamic_enum:")) {
            node.put("type", "enum");
            String dirPath = resolvedType.substring("dynamic_enum:".length());
            node.put("options", scanDynamicEnumOptions(moduleId, dirPath));
        } else {
            node.put("type", resolvedType);
        }
        node.put("editable", editable);
        node.put("value", value);
        if (meta.creatableChildren()) {
            node.put("creatableChildren", true);
        }
        List<Map<String, Object>> templates = nodeCreateTemplates(moduleId, path);
        if (!templates.isEmpty()) {
            node.put("createTemplates", templates);
        }
        List<Map<String, Object>> itemFields = nodeListItemFields(moduleId, path);
        if (!itemFields.isEmpty()) {
            node.put("itemFields", itemFields);
        }
        String uniqueBy = nodeUniqueBy(moduleId, path);
        if (Texts.isNotBlank(uniqueBy)) {
            node.put("uniqueBy", uniqueBy);
        }
        return node;
    }

    private static synchronized List<Map<String, Object>> nodeCreateTemplates(String moduleId, String path) {
        return copySchemaList(CREATE_TEMPLATES.getOrDefault(key(moduleId, path), CREATE_TEMPLATES.getOrDefault(key("*", path), List.of())));
    }

    private static synchronized List<Map<String, Object>> nodeListItemFields(String moduleId, String path) {
        return copySchemaList(LIST_ITEM_FIELDS.getOrDefault(key(moduleId, path), LIST_ITEM_FIELDS.getOrDefault(key("*", path), List.of())));
    }

    private static synchronized String nodeUniqueBy(String moduleId, String path) {
        return UNIQUE_LIST_FIELDS.getOrDefault(key(moduleId, path), UNIQUE_LIST_FIELDS.getOrDefault(key("*", path), ""));
    }

    private static List<Map<String, Object>> copySchemaList(List<Map<String, Object>> entries) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> entry : entries) {
            result.add(new LinkedHashMap<>(entry));
        }
        return result;
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
                        String name = entry.getName();
                        result.add(name.substring(0, name.lastIndexOf('.')));
                    }
                } catch (Exception ignored) {
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
        return new NodeMeta(lastKey(path).replace('_', ' '), "", detectedType, false);
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

    private String resolveNodeType(String detectedType, String metaType) {
        if (Texts.isBlank(metaType)) {
            return detectedType;
        }
        if ("list".equals(detectedType)) {
            return isListUiType(metaType) ? metaType : detectedType;
        }
        if ("boolean".equals(detectedType)) {
            return "boolean";
        }
        if ("number".equals(detectedType)) {
            return "number";
        }
        if ("object".equals(detectedType)) {
            return "dynamic_map".equals(metaType) ? metaType : "object";
        }
        return metaType;
    }

    private boolean isListUiType(String type) {
        return "list".equals(type)
                || "stringList".equals(type)
                || "numberList".equals(type)
                || "objectList".equals(type)
                || "actions".equals(type);
    }

    private Object normalizeValue(Object value) {
        if (value instanceof YamlSection section) {
            return section.asMap();
        }
        return value;
    }

    private Object normalizeIncomingValue(Object current, Object value) {
        if (current == null) {
            return normalizeNewValue(value);
        }
        if (current instanceof Boolean) {
            return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
        }
        if (current instanceof Number) {
            return parseNumber(value);
        }
        if (current instanceof List<?>) {
            return value instanceof List<?> list ? normalizeListValue(list) : Arrays.stream(String.valueOf(value).split("\\n"))
                    .map(String::trim)
                    .filter(entry -> !entry.isEmpty())
                    .toList();
        }
        return value == null ? "" : String.valueOf(value);
    }

    private Object normalizeNewValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value;
        }
        if (value instanceof List<?> list) {
            return normalizeListValue(list);
        }
        if (value instanceof Map<?, ?> || value instanceof YamlSection) {
            return normalizePlainValue(value);
        }
        return String.valueOf(value);
    }

    private List<Object> normalizeListValue(List<?> list) {
        List<Object> result = new ArrayList<>();
        for (Object entry : list) {
            result.add(normalizePlainValue(entry));
        }
        return result;
    }

    private Object normalizePlainValue(Object value) {
        if (value instanceof YamlSection section) {
            return normalizePlainValue(section.asMap());
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                result.put(String.valueOf(entry.getKey()), normalizePlainValue(entry.getValue()));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            return normalizeListValue(list);
        }
        return value;
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

    public static synchronized List<WebRegisteredFileEntry> registeredFileEntries() {
        List<WebRegisteredFileEntry> result = new ArrayList<>();
        for (ModuleRegistration registration : MODULES.values()) {
            Plugin installed = Bukkit.getPluginManager().getPlugin(registration.id());
            if (installed == null || !installed.isEnabled()) {
                continue;
            }
            for (FileRegistration file : registration.files()) {
                result.add(new WebRegisteredFileEntry(
                        registration.id(),
                        file.title(),
                        file.relativePath(),
                        file.type().kind(),
                        file.structuredYaml()
                ));
            }
        }
        return List.copyOf(result);
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

    private static List<Map<String, Object>> guiTypes() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (InventoryType type : InventoryType.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", type.name());
            entry.put("defaultTitle", type.getDefaultTitle());
            entry.put("defaultSize", type.getDefaultSize());
            entry.put("supportsRows", type == InventoryType.CHEST);
            entry.put("creatable", isCreatableInventoryType(type));
            result.add(entry);
        }
        return result;
    }

    private static Map<String, List<String>> runtimeEnums() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("bukkit.damageCause", Arrays.stream(EntityDamageEvent.DamageCause.values()).map(Enum::name).toList());
        return result;
    }

    private static boolean isCreatableInventoryType(InventoryType type) {
        if (type == null || type == InventoryType.CHEST) {
            return false;
        }
        return switch (type) {
            case CRAFTING, CREATIVE, PLAYER, MERCHANT -> false;
            default -> type.getDefaultSize() > 0;
        };
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
            String version = installed.getDescription().getVersion();
            entry.put("url", "/extensions/" + extension.moduleId() + "/" + extension.resourcePath() + "?v=" + version);
            entry.put("apiVersion", "1.1.0");
            result.add(entry);
        }
        return result;
    }

    private static Map<String, Object> mergeEditorFields(Object existingFields, Object incomingFields) {
        Map<String, Object> result = new LinkedHashMap<>();
        copyEditorFields(result, existingFields);
        copyEditorFields(result, incomingFields);
        return result;
    }

    private static void copyEditorFields(Map<String, Object> target, Object fieldsValue) {
        if (!(fieldsValue instanceof Map<?, ?> fields)) {
            return;
        }
        for (Map.Entry<?, ?> entry : fields.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            target.put(String.valueOf(entry.getKey()), entry.getValue());
        }
    }

    private static Map<String, Object> editorField(String path, String label, String comment, String type) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("path", path);
        field.put("label", label);
        if (Texts.isNotBlank(comment)) {
            field.put("comment", comment);
        }
        if (Texts.isNotBlank(type)) {
            field.put("type", type);
        }
        return field;
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
                .filter(file -> file.type().is("CONFIG") && file.structuredYaml())
                .findFirst()
                .orElse(null);
    }

    private static FileRegistration configByPath(ModuleRegistration registration, String relativePath) {
        return registration.files().stream()
                .filter(file -> file.type().is("CONFIG") && file.structuredYaml() && file.relativePath().equals(relativePath))
                .findFirst()
                .orElse(null);
    }

    private static void registerFile(String moduleId, String title, String relativePath, WebConsoleFileType type, String comment, boolean structuredYaml) {
        registerFile(moduleId, title, relativePath, type, comment, structuredYaml, "");
    }

    private static void registerFile(String moduleId, String title, String relativePath, WebConsoleFileType type, String comment, boolean structuredYaml, String editorId) {
        ModuleRegistration module = MODULES.get(moduleId);
        if (module == null) {
            registerModule(moduleId, moduleId, "", "default");
            module = MODULES.get(moduleId);
        }
        FileRegistration next = new FileRegistration(title, relativePath, type, comment, structuredYaml, Texts.toStringSafe(editorId));
        boolean exists = module.files().stream().anyMatch(file -> file.relativePath().equals(relativePath) && file.type().equals(type));
        if (!exists) {
            module.files().add(next);
        }
    }

    private static void registerCommonComments() {
        registerNodeComment("*", "version", "", "", "text");
        registerNodeComment("*", "language", "", "", "text");
        registerNodeComment("*", "release_default_data", "", "", "boolean");

        registerNodeComment("EmakiCoreLib", "web_console", "Web Console", "内置 Web 管理台服务、认证和安全设置。", "object");
        registerNodeComment("EmakiCoreLib", "web_console.enabled", "启用 Web Console", "是否启动内置 Web 管理台。", "boolean");
        registerNodeComment("EmakiCoreLib", "web_console.host", "监听地址", "Web Console 绑定的主机地址。", "text");
        registerNodeComment("EmakiCoreLib", "web_console.port", "监听端口", "Web Console 监听端口，范围 1-65535。", "number");
        registerNodeComment("EmakiCoreLib", "web_console.public_access_warning", "公网警告", "监听非本地地址时是否输出安全警告。", "boolean");
        registerNodeComment("EmakiCoreLib", "web_console.auth", "认证", "Web Console 登录认证设置。", "object");
        registerNodeComment("EmakiCoreLib", "web_console.auth.username", "用户名", "Web Console 登录用户名。", "text");
        registerNodeComment("EmakiCoreLib", "web_console.auth.password", "密码", "Web Console 登录密码，生产环境必须修改默认值。", "text");
        registerNodeComment("EmakiCoreLib", "web_console.auth.session_timeout_minutes", "会话超时", "登录会话超时时间，单位分钟。", "number");
        registerNodeComment("EmakiCoreLib", "web_console.security", "安全", "Web Console 写入权限和请求限制。", "object");
        registerNodeComment("EmakiCoreLib", "web_console.security.allow_config_write", "允许写配置", "是否允许通过 Web Console 保存配置文件。", "boolean");
        registerNodeComment("EmakiCoreLib", "web_console.security.max_request_body_kb", "请求体上限", "Web Console 请求体最大大小，单位 KB。", "number");
        registerNodeComment("EmakiCoreLib", "web_console.security.allowed_modules", "允许模块", "允许通过 Web Console 访问的模块白名单；留空表示不限制。", "list");
        registerNodeComment("EmakiCoreLib", "web_console.config_browser", "文件浏览", "Web Console 文件浏览器大小与扩展名限制。", "object");
        registerNodeComment("EmakiCoreLib", "web_console.config_browser.max_file_size_kb", "文件大小上限", "允许读取的单文件最大大小，单位 KB。", "number");
        registerNodeComment("EmakiCoreLib", "web_console.config_browser.allowed_extensions", "允许扩展名", "文件浏览器允许读取的扩展名列表。", "list");
        registerNodeComment("EmakiCoreLib", "web_console.history", "变更历史", "Web Console 写入历史、快照保留和删除备份设置。", "object");
        registerNodeComment("EmakiCoreLib", "web_console.history.enabled", "启用历史", "是否记录 Web Console 写入历史。", "boolean");
        registerNodeComment("EmakiCoreLib", "web_console.history.max_snapshots_per_file", "单文件快照上限", "每个文件最多保留的历史快照数量。", "number");
        registerNodeComment("EmakiCoreLib", "web_console.history.max_age_days", "历史保留天数", "历史快照最多保留的天数。", "number");
        registerNodeComment("EmakiCoreLib", "web_console.history.record_web_writes", "记录写入", "是否记录保存、创建和回滚等 Web 写入操作。", "boolean");
        registerNodeComment("EmakiCoreLib", "web_console.history.record_delete_backup", "删除备份", "删除文件前是否保留历史备份。", "boolean");
        registerNodeComment("EmakiCoreLib", "action", "动作", "CoreLib Action 全局配置。", "object");
        registerCreatableNode("EmakiCoreLib", "action.templates", "动作模板", "可通过 @template=名称 引用的动作模板映射。", "object");
        registerNodeKeyComment("EmakiCoreLib", "templates", "动作模板", "可通过 @template=名称 引用的动作模板映射，模板值为 CoreLib Action 行列表。", "object");
        registerNodeComment("EmakiCoreLib", "script", "脚本", "GraalJS 脚本引擎、路径、动作别名、上下文暴露和安全限制。", "object");
        registerNodeComment("EmakiCoreLib", "script.enabled", "启用脚本", "是否启用 CoreLib JavaScript 动作。", "boolean");
        registerNodeComment("EmakiCoreLib", "script.engine", "脚本引擎", "脚本运行引擎和超时/安全能力。", "object");
        registerNodeComment("EmakiCoreLib", "script.engine.type", "引擎类型", "脚本引擎类型，目前为 graaljs。", "text");
        registerNodeComment("EmakiCoreLib", "script.engine.default_timeout_millis", "默认超时", "脚本默认执行超时时间，支持毫秒或时间文本。", "text");
        registerNodeComment("EmakiCoreLib", "script.engine.max_timeout_millis", "最大超时", "脚本允许的最大执行超时时间。", "text");
        registerNodeComment("EmakiCoreLib", "script.engine.cache_enabled", "启用缓存", "是否缓存编译后的脚本。", "boolean");
        registerNodeComment("EmakiCoreLib", "script.engine.recompile_on_reload", "重载重编译", "插件重载时是否重新编译脚本。", "boolean");
        registerNodeComment("EmakiCoreLib", "script.engine.allow_host_access", "允许 Host 访问", "是否允许脚本访问宿主对象能力。", "boolean");
        registerNodeComment("EmakiCoreLib", "script.engine.allow_host_class_lookup", "允许类查找", "是否允许脚本查找宿主 Java 类。", "boolean");
        registerNodeComment("EmakiCoreLib", "script.engine.allow_io", "允许 IO", "是否允许脚本执行 IO 操作。", "boolean");
        registerNodeComment("EmakiCoreLib", "script.engine.allow_threads", "允许线程", "是否允许脚本创建或操作线程。", "boolean");
        registerNodeComment("EmakiCoreLib", "script.engine.allow_native_access", "允许原生访问", "是否允许脚本访问原生能力。", "boolean");
        registerNodeComment("EmakiCoreLib", "script.engine.allow_environment_access", "允许环境访问", "是否允许脚本读取环境变量。", "boolean");
        registerNodeComment("EmakiCoreLib", "script.paths", "脚本路径", "脚本根目录和自动创建目录。", "object");
        registerNodeComment("EmakiCoreLib", "script.paths.root", "脚本根目录", "脚本文件根目录。", "text");
        registerNodeComment("EmakiCoreLib", "script.paths.create_directories", "创建目录", "启动时确保存在的脚本子目录列表。", "list");
        registerNodeComment("EmakiCoreLib", "script.action", "脚本动作", "runjs 动作 ID、别名和默认函数设置。", "object");
        registerNodeComment("EmakiCoreLib", "script.action.id", "动作 ID", "脚本动作主 ID。", "text");
        registerNodeComment("EmakiCoreLib", "script.action.aliases", "动作别名", "脚本动作别名列表。", "list");
        registerNodeComment("EmakiCoreLib", "script.action.default_function", "默认函数", "未指定函数时调用的脚本函数名。", "text");
        registerNodeComment("EmakiCoreLib", "script.action.stop_on_failure", "失败停止", "脚本动作失败时是否停止动作链。", "boolean");
        registerNodeComment("EmakiCoreLib", "script.context", "脚本上下文", "暴露给脚本的上下文对象开关。", "object");
        registerNodeSuffixComment("EmakiCoreLib", ".expose_context", "暴露上下文", "是否向脚本暴露上下文对象。", "boolean");
        registerNodeSuffixComment("EmakiCoreLib", ".expose_player", "暴露玩家", "是否向脚本暴露玩家对象。", "boolean");
        registerNodeSuffixComment("EmakiCoreLib", ".expose_item", "暴露物品", "是否向脚本暴露物品对象。", "boolean");
        registerNodeSuffixComment("EmakiCoreLib", ".expose_action", "暴露动作", "是否向脚本暴露动作参数。", "boolean");
        registerNodeSuffixComment("EmakiCoreLib", ".expose_logger", "暴露日志", "是否向脚本暴露日志接口。", "boolean");
        registerNodeSuffixComment("EmakiCoreLib", ".expose_random", "暴露随机", "是否向脚本暴露随机数工具。", "boolean");
        registerNodeSuffixComment("EmakiCoreLib", ".expose_shared_state", "暴露共享状态", "是否向脚本暴露共享状态。", "boolean");
        registerNodeSuffixComment("EmakiCoreLib", ".expose_text", "暴露文本", "是否向脚本暴露文本工具。", "boolean");
        registerNodeComment("EmakiCoreLib", "script.security", "脚本安全", "脚本路径、动作派发和递归深度限制。", "object");
        registerNodeComment("EmakiCoreLib", "script.security.denied_path_fragments", "禁用路径片段", "脚本路径中禁止出现的片段。", "list");
        registerNodeComment("EmakiCoreLib", "script.security.denied_actions_from_script", "脚本禁用动作", "脚本内禁止再次派发的动作 ID 列表。", "list");
        registerNodeComment("EmakiCoreLib", "script.security.allow_action_dispatch", "允许动作派发", "是否允许脚本派发 CoreLib Action。", "boolean");
        registerNodeComment("EmakiCoreLib", "script.security.max_action_depth", "最大动作深度", "脚本派发动作的最大递归深度。", "number");
        registerNodeComment("EmakiCoreLib", "script.debug", "脚本调试", "脚本加载、执行和异常堆栈日志开关。", "object");
        registerNodeComment("EmakiCoreLib", "script.debug.log_script_load", "记录加载", "是否记录脚本加载日志。", "boolean");
        registerNodeComment("EmakiCoreLib", "script.debug.log_script_execute", "记录执行", "是否记录脚本执行日志。", "boolean");
        registerNodeComment("EmakiCoreLib", "script.debug.print_stacktrace", "打印堆栈", "脚本异常时是否打印堆栈。", "boolean");

        registerNodeKeyComment("*", "enabled", "", "", "boolean");
        registerNodeKeyComment("*", "debug", "", "", "object");
        registerNodeKeyComment("*", "permission", "", "", "object");
        registerNodeKeyComment("*", "gui", "", "", "object");
        registerNodeKeyComment("*", "actions", "", "", "object");
        registerNodeKeyComment("*", "success", "", "", "list");
        registerNodeKeyComment("*", "failure", "", "", "list");
        registerNodeKeyComment("*", "item_sources", "", "", "list");
        registerNodeKeyComment("*", "material", "", "", "text");
        registerNodeKeyComment("*", "amount", "", "", "number");
        registerNodeKeyComment("*", "title", "", "", "text");
        registerNodeKeyComment("*", "size", "", "", "number");
        registerNodeKeyComment("*", "slots", "", "", "list");
        registerNodeKeyComment("*", "items", "", "", "object");
        registerNodeKeyComment("*", "commands", "", "", "list");
        registerNodeKeyComment("*", "chance", "", "", "number");
        registerNodeSuffixComment("*", ".enabled", "", "", "boolean");
        registerNodeSuffixComment("*", ".default_chance", "", "", "number");
        registerNodeSuffixComment("*", ".op_bypass", "", "", "boolean");
        registerNodeContainsComment("*", ".actions.", "", "", "list");
    }


    private static String fileId(String moduleId, FileRegistration registration) {
        return (moduleId + "-" + registration.type().kind() + "-" + registration.relativePath())
                .toLowerCase(java.util.Locale.ROOT)
                .replace("**/", "")
                .replace("*", "all")
                .replace('/', '-')
                .replace('\\', '-')
                .replace('.', '-');
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


    public record WebConsoleFileType(String kind) {
        public static final WebConsoleFileType CONFIG = new WebConsoleFileType("CONFIG");
        public static final WebConsoleFileType GUI = new WebConsoleFileType("GUI");
        public static final WebConsoleFileType ITEM = new WebConsoleFileType("ITEM");
        public static final WebConsoleFileType SCRIPT = new WebConsoleFileType("SCRIPT");

        public WebConsoleFileType {
            kind = Texts.isBlank(kind) ? "CONFIG" : kind.trim().toUpperCase(java.util.Locale.ROOT);
        }

        public static WebConsoleFileType resource(String kind) {
            return switch (Texts.toStringSafe(kind).trim().toUpperCase(java.util.Locale.ROOT)) {
                case "CONFIG" -> CONFIG;
                case "GUI" -> GUI;
                case "ITEM" -> ITEM;
                case "SCRIPT" -> SCRIPT;
                default -> new WebConsoleFileType(kind);
            };
        }

        public boolean is(String expected) {
            return kind.equalsIgnoreCase(Texts.toStringSafe(expected));
        }
    }

    private enum MatchType {
        SUFFIX,
        CONTAINS,
        KEY
    }

    public record WebRegisteredFileEntry(String moduleId, String title, String relativePath, String kind, boolean structuredYaml) {}
    private record ModuleRegistration(String id, String name, String summary, String tone, String iconSvg, List<FileRegistration> files) {}
    private record EditorRegistration(String moduleId, String editorId, Map<String, Object> descriptor) {}
    private record WebExtensionRegistration(String moduleId, String id, String resourcePath) {}
    private record FileRegistration(String title, String relativePath, WebConsoleFileType type, String comment, boolean structuredYaml, String editorId) {}
    private record NodeMeta(String label, String comment, String type, boolean creatableChildren) {}

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
            return new NodeMeta(label, comment, type, false);
        }
    }
}
