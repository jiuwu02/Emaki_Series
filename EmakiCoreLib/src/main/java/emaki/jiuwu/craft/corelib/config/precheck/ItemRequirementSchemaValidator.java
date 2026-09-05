package emaki.jiuwu.craft.corelib.config.precheck;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.matcher.ItemRequirement;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;

public final class ItemRequirementSchemaValidator {

    public enum Role {
        INPUT,
        MATERIAL,
        OUTPUT,
        PERSISTENT
    }

    private static final List<String> SOURCE_KEYS = List.of("item_sources", "item_source", "sources", "source");
    private static final List<String> OUTPUT_SOURCE_KEYS = List.of("item_source", "item_sources", "source", "sources");
    private static final List<String> MATCHER_KEYS = List.of(
            "matcher", "matchers", "item_matcher", "input_matcher", "material_matcher", "tool_matcher", "container_matcher");
    private static final List<String> IDENTITY_KEYS = List.of("id", "material_id", "requirement_id", "count_key", "slot_id", "audit_id");
    private static final List<String> MATCHER_SOURCE_TYPES = List.of("item_source", "item_sources", "source", "sources");

    private ItemRequirementSchemaValidator() {
    }

    public static List<ConfigPrecheckIssue> validate(String module, String path, Object node, Role role) {
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        Map<String, Object> values = mapping(node);
        if (values == null) {
            issues.add(issue(module, path, ConfigPrecheckSeverity.ERROR, "item requirement must be a mapping"));
            return List.copyOf(issues);
        }
        Role resolvedRole = role == null ? Role.INPUT : role;
        List<String> sourceKeys = resolvedRole == Role.OUTPUT ? OUTPUT_SOURCE_KEYS : SOURCE_KEYS;
        validateAliases(module, path, values, sourceKeys, "item source", issues);
        validateAliases(module, path, values, MATCHER_KEYS, "matcher", issues);
        validateIdentityFields(module, path, values, issues);
        validateAmount(module, path, values, resolvedRole, issues);
        validateSource(module, path, values, sourceKeys, resolvedRole, issues);
        validateMatcher(module, path, values, resolvedRole, issues);
        if (resolvedRole != Role.OUTPUT && resolvedRole != Role.PERSISTENT
                && !containsAny(values, SOURCE_KEYS) && !containsAny(values, MATCHER_KEYS)) {
            issues.add(issue(module, path, ConfigPrecheckSeverity.ERROR,
                    "item requirement must declare item_sources or matcher"));
        }
        if (resolvedRole == Role.PERSISTENT && !containsAny(values, IDENTITY_KEYS)) {
            issues.add(issue(module, path, ConfigPrecheckSeverity.ERROR,
                    "persistent item requirement must declare a stable identity field"));
        }
        if (resolvedRole == Role.PERSISTENT && values.containsKey("persistent")
                && !(values.get("persistent") instanceof Boolean)) {
            issues.add(issue(module, field(path, "persistent"), ConfigPrecheckSeverity.ERROR,
                    "persistent must be a boolean"));
        }
        return List.copyOf(issues);
    }

    public static List<ConfigPrecheckIssue> validateAll(String module, String path,
            Collection<?> nodes, Role role) {
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        Map<String, Map<String, Integer>> identities = new HashMap<>();
        int index = 0;
        for (Object node : nodes == null ? List.of() : nodes) {
            String nodePath = indexed(path, index);
            issues.addAll(validate(module, nodePath, node, role));
            Map<String, Object> values = mapping(node);
            if (values != null) {
                for (String key : IDENTITY_KEYS) {
                    if ("count_key".equals(key)) {
                        continue;
                    }
                    Object value = values.get(key);
                    if (!(value instanceof String text) || Texts.isBlank(text)) {
                        continue;
                    }
                    String normalized = ItemRequirement.normalizeIdentity(text);
                    Map<String, Integer> seen = identities.computeIfAbsent(key, ignored -> new HashMap<>());
                    Integer previous = seen.putIfAbsent(normalized, index);
                    if (previous != null) {
                        issues.add(issue(module, nodePath + "." + key, ConfigPrecheckSeverity.ERROR,
                                "duplicate " + key + " identity '" + normalized + "', first declared at "
                                        + indexed(path, previous)));
                    }
                }
            }
            index++;
        }
        return List.copyOf(issues);
    }

    public static boolean blocking(List<ConfigPrecheckIssue> issues) {
        if (issues == null) {
            return false;
        }
        return issues.stream().anyMatch(issue -> issue.severity().blocking());
    }

    public static List<ConfigPrecheckIssue> validateEntries(String module,
            String directory,
            Map<String, ? extends YamlDirectoryLoader.LoadedYamlEntry<?>> entries) {
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        if (entries == null) {
            return List.of();
        }
        for (YamlDirectoryLoader.LoadedYamlEntry<?> entry : entries.values()) {
            if (entry == null || entry.configuration() == null) {
                continue;
            }
            String base = directory + "/" + entry.file().getName();
            scan(module, base, entry.configuration(), "", issues);
        }
        return List.copyOf(issues);
    }

    public static List<ConfigPrecheckIssue> validateDocument(String module, String path, YamlSection document) {
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        scan(module, path, document, "", issues);
        return List.copyOf(issues);
    }

    public static List<ConfigPrecheckIssue> validateFile(String module, File file, String path) {
        if (file == null || !file.isFile()) {
            return List.of();
        }
        try {
            return validateDocument(module, path, YamlFiles.load(file));
        } catch (RuntimeException exception) {
            return List.of(issue(module, path, ConfigPrecheckSeverity.ERROR,
                    "unable to parse item requirement document: " + Texts.toStringSafe(exception.getMessage())));
        }
    }

    public static List<ConfigPrecheckIssue> validateDirectory(String module, File directory, String path) {
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        for (File file : YamlDirectoryLoader.collectYamlFiles(directory)) {
            String relative = directory.toPath().relativize(file.toPath()).toString().replace('\\', '/');
            issues.addAll(validateFile(module, file, path + "/" + relative));
        }
        return List.copyOf(issues);
    }

    private static void scan(String module, String filePath, Object node, String nodePath,
            List<ConfigPrecheckIssue> issues) {
        Map<String, Object> values = mapping(node);
        if (values == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String path = nodePath.isEmpty() ? key : nodePath + "." + key;
            if (value instanceof Collection<?> collection) {
                Role role = collectionRole(key);
                if (role != null) {
                    List<Object> mapped = new ArrayList<>();
                    for (Object child : collection) {
                        if (mapping(child) != null) {
                            mapped.add(child);
                        }
                    }
                    if (!mapped.isEmpty()) {
                        issues.addAll(validateAll(module, filePath + ":" + path, mapped, role));
                    }
                }
                int index = 0;
                for (Object child : collection) {
                    String childPath = path + "[" + index + "]";
                    scan(module, filePath, child, childPath, issues);
                    index++;
                }
                continue;
            }
            Map<String, Object> child = mapping(value);
            if (child == null) {
                continue;
            }
            Role role = nodeRole(key);
            if (role != null) {
                addValidated(module, filePath + ":" + path, child, role, issues);
            }
            scan(module, filePath, child, path, issues);
        }
    }

    private static void addValidated(String module, String path, Object node, Role role,
            List<ConfigPrecheckIssue> issues) {
        if (!looksLikeRequirement(node, role)) {
            return;
        }
        issues.addAll(validate(module, path, node, role));
    }

    private static boolean looksLikeRequirement(Object node, Role role) {
        Map<String, Object> values = mapping(node);
        if (values == null) {
            return false;
        }
        if (role == Role.OUTPUT) {
            return containsAny(values, SOURCE_KEYS);
        }
        return containsAny(values, SOURCE_KEYS) || containsAny(values, MATCHER_KEYS)
                || values.containsKey("amount") || containsAny(values, IDENTITY_KEYS);
    }

    private static Role collectionRole(String key) {
        return switch (key) {
            case "inputs", "ingredients", "requirements", "blueprint_requirements" -> Role.INPUT;
            case "materials", "costs" -> Role.MATERIAL;
            case "outputs", "results", "rewards" -> Role.OUTPUT;
            default -> null;
        };
    }

    private static Role nodeRole(String key) {
        return switch (key) {
            case "input", "ingredient", "requirement", "target", "tool", "container" -> Role.INPUT;
            case "material", "cost" -> Role.MATERIAL;
            case "output", "result", "success", "failure", "item" -> Role.OUTPUT;
            default -> null;
        };
    }

    private static void validateAliases(String module, String path, Map<String, Object> values,
            List<String> aliases, String label, List<ConfigPrecheckIssue> issues) {
        List<String> declared = declared(values, aliases);
        for (String key : declared) {
            if ("matchers".equals(key) && "matcher".equals(aliases.getFirst())) {
                issues.add(issue(module, field(path, key), ConfigPrecheckSeverity.ERROR,
                        "top-level 'matchers' is not an item requirement matcher; use 'matcher'"));
            } else if (!key.equals(aliases.getFirst())) {
                issues.add(issue(module, field(path, key), ConfigPrecheckSeverity.WARN,
                        "legacy " + label + " field '" + key + "'; use '" + aliases.getFirst() + "'"));
            }
        }
        if (declared.size() > 1) {
            issues.add(issue(module, path, ConfigPrecheckSeverity.ERROR,
                    "conflicting top-level " + label + " fields: " + String.join(", ", declared)));
        }
    }

    private static void validateIdentityFields(String module, String path, Map<String, Object> values,
            List<ConfigPrecheckIssue> issues) {
        for (String key : IDENTITY_KEYS) {
            if (!values.containsKey(key)) {
                continue;
            }
            Object value = values.get(key);
            if (!(value instanceof String text)) {
                issues.add(issue(module, field(path, key), ConfigPrecheckSeverity.ERROR,
                        key + " must be a string"));
            } else if (Texts.isBlank(text)) {
                issues.add(issue(module, field(path, key), ConfigPrecheckSeverity.ERROR,
                        key + " must not be blank"));
            }
        }
    }

    private static void validateAmount(String module, String path, Map<String, Object> values,
            Role role, List<ConfigPrecheckIssue> issues) {
        if (!values.containsKey("amount")) {
            if (role == Role.MATERIAL) {
                issues.add(issue(module, field(path, "amount"), ConfigPrecheckSeverity.ERROR,
                        "material amount is required"));
            }
            return;
        }
        Object value = values.get("amount");
        if (!(value instanceof Number number) || number.doubleValue() % 1.0D != 0.0D || number.intValue() <= 0) {
            issues.add(issue(module, field(path, "amount"), ConfigPrecheckSeverity.ERROR,
                    "amount must be a positive integer"));
        }
    }

    private static void validateSource(String module, String path, Map<String, Object> values,
            List<String> sourceAliases, Role role, List<ConfigPrecheckIssue> issues) {
        List<String> keys = declared(values, sourceAliases);
        if (keys.isEmpty()) {
            if (role == Role.OUTPUT) {
                issues.add(issue(module, path, ConfigPrecheckSeverity.ERROR,
                        "output must declare exactly one item_source"));
            }
            return;
        }
        String key = keys.getFirst();
        List<Object> rawSources = ConfigNodes.asObjectList(values.get(key));
        int valid = 0;
        for (Object rawSource : rawSources) {
            if (ItemSourceUtil.parse(rawSource) != null) {
                valid++;
            } else {
                issues.add(issue(module, field(path, key), ConfigPrecheckSeverity.WARN,
                        "item source entry is invalid: " + String.valueOf(rawSource)));
            }
        }
        if (valid == 0) {
            issues.add(issue(module, field(path, key), ConfigPrecheckSeverity.ERROR,
                    "item source field must contain at least one valid source"));
        }
        if (role == Role.OUTPUT && (rawSources.size() != 1 || valid != 1)) {
            issues.add(issue(module, field(path, key), ConfigPrecheckSeverity.ERROR,
                    "output must declare exactly one item_source"));
        }
    }

    private static void validateMatcher(String module, String path, Map<String, Object> values,
            Role role, List<ConfigPrecheckIssue> issues) {
        List<String> keys = declared(values, MATCHER_KEYS);
        if (keys.isEmpty()) {
            return;
        }
        String key = keys.getFirst();
        if (role == Role.OUTPUT || role == Role.PERSISTENT) {
            issues.add(issue(module, field(path, key), ConfigPrecheckSeverity.ERROR,
                    role.name().toLowerCase(Locale.ROOT) + " nodes must not declare matcher conditions"));
            return;
        }
        validateMatcherNode(module, field(path, key), values.get(key), issues);
    }

    private static void validateMatcherNode(String module, String path, Object node,
            List<ConfigPrecheckIssue> issues) {
        Map<String, Object> values = mapping(node);
        if (values == null) {
            issues.add(issue(module, path, ConfigPrecheckSeverity.ERROR, "matcher must be a mapping"));
            return;
        }
        for (String sourceKey : SOURCE_KEYS) {
            if (values.containsKey(sourceKey)) {
                issues.add(issue(module, field(path, sourceKey), ConfigPrecheckSeverity.ERROR,
                        "matcher must not contain item source fields"));
            }
        }
        Object typeValue = values.get("type");
        if (!(typeValue instanceof String type) || Texts.isBlank(type)) {
            issues.add(issue(module, field(path, "type"), ConfigPrecheckSeverity.ERROR,
                    "matcher type must be a non-blank string"));
        } else if (MATCHER_SOURCE_TYPES.contains(type.trim().toLowerCase(Locale.ROOT))) {
            issues.add(issue(module, field(path, "type"), ConfigPrecheckSeverity.ERROR,
                    "matcher item source type '" + type + "' is not allowed"));
        }
        Object children = values.get("matchers");
        if (children == null) {
            return;
        }
        if (!(children instanceof Collection<?> collection)) {
            issues.add(issue(module, field(path, "matchers"), ConfigPrecheckSeverity.ERROR,
                    "matcher children must be a list"));
            return;
        }
        int index = 0;
        for (Object child : collection) {
            validateMatcherNode(module, field(path, "matchers") + "[" + index + "]", child, issues);
            index++;
        }
    }

    private static Map<String, Object> mapping(Object node) {
        if (node == null) {
            return null;
        }
        if (node instanceof Map<?, ?> || node instanceof emaki.jiuwu.craft.corelib.api.yaml.YamlSection) {
            return new LinkedHashMap<>(ConfigNodes.entries(node));
        }
        return null;
    }

    private static boolean containsAny(Map<String, Object> values, List<String> keys) {
        return keys.stream().anyMatch(values::containsKey);
    }

    private static List<String> declared(Map<String, Object> values, List<String> keys) {
        List<String> result = new ArrayList<>();
        for (String key : keys) {
            if (values.containsKey(key)) {
                result.add(key);
            }
        }
        return result;
    }

    private static ConfigPrecheckIssue issue(String module, String path,
            ConfigPrecheckSeverity severity, String message) {
        return ConfigPrecheckIssue.of(module, path, severity, message);
    }

    private static String indexed(String path, int index) {
        return Texts.isBlank(path) ? "[" + index + "]" : path + "[" + index + "]";
    }

    private static String field(String path, String key) {
        return Texts.isBlank(path) ? key : path + "." + key;
    }
}
