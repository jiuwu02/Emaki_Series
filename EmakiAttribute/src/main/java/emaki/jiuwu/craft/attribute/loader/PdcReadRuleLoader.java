package emaki.jiuwu.craft.attribute.loader;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.PdcReadRule;
import emaki.jiuwu.craft.attribute.model.PdcReadRule.RuleCondition;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public final class PdcReadRuleLoader extends DirectoryLoader<PdcReadRule> {

    public PdcReadRuleLoader(EmakiAttributePlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "conditions";
    }

    @Override
    protected String typeName() {
        return "PDC 读取条件";
    }

    @Override
    protected void seedBundledResources(File directory) {
        List<String> bundledResources = YamlFiles.listResourcePaths(plugin, directoryName());
        String resourcePrefix = directoryName() + "/";
        for (String resourceName : bundledResources) {
            String relativePath = resourceName.startsWith(resourcePrefix)
                    ? resourceName.substring(resourcePrefix.length())
                    : resourceName;
            copyBundledResource(resourceName, new File(directory, relativePath));
        }
    }

    @Override
    protected boolean validateSchema(File file, YamlSection configuration) {
        if (configuration == null || !Boolean.TRUE.equals(configuration.getBoolean("enabled", true))) {
            return true;
        }
        if (Texts.isBlank(configuration.getString("source_id"))) {
            issue(
                    "loader.schema_missing_id",
                    Map.of(
                            "type", typeName(),
                            "file", file.getName(),
                            "field", "source_id"
                    )
            );
            return false;
        }
        PdcReadRule rule = PdcReadRule.fromMap(configuration.asMap());
        if (rule == null) {
            return false;
        }
        if (hasUnsupportedChecksField(configuration)) {
            issue(
                    "loader.schema_unsupported_field",
                    Map.of(
                            "type", typeName(),
                            "file", file.getName(),
                            "field", "checks"
                    )
            );
            return false;
        }
        if (!hasValidConditionEntries(configuration, rule)) {
            issue(
                    "loader.schema_invalid_section",
                    Map.of(
                            "type", typeName(),
                            "file", file.getName(),
                            "field", "conditions"
                    )
            );
            return false;
        }
        for (RuleCondition condition : rule.conditions()) {
            if (!isSupportedConditionType(condition.type())) {
                issue(
                        "loader.schema_invalid_enum",
                        Map.of(
                                "type", typeName(),
                                "file", file.getName(),
                                "field", "conditions.type"
                        )
                );
                return false;
            }
            if (requiresKey(condition.type()) && Texts.isBlank(condition.key())) {
                issue(
                        "loader.schema_missing_id",
                        Map.of(
                                "type", typeName(),
                                "file", file.getName(),
                                "field", "conditions.key"
                        )
                );
                return false;
            }
            if ("lore_regex".equals(normalizeConditionType(condition.type())) && Texts.isBlank(condition.pattern())) {
                issue(
                        "loader.schema_missing_section",
                        Map.of(
                                "type", typeName(),
                                "file", file.getName(),
                                "field", "conditions.pattern"
                        )
                );
                return false;
            }
            if (Texts.isNotBlank(condition.pattern()) && !isValidRegex(condition.pattern())) {
                issue(
                        "loader.load_failed",
                        Map.of(
                                "type", typeName(),
                                "file", file.getName(),
                                "error", "无效正则: " + condition.pattern()
                        )
                );
                return false;
            }
        }
        return true;
    }

    @Override
    protected PdcReadRule parse(File file, YamlSection configuration) {
        if (configuration == null || !Boolean.TRUE.equals(configuration.getBoolean("enabled", true))) {
            return null;
        }
        return PdcReadRule.fromMap(configuration.asMap());
    }

    @Override
    protected String idOf(PdcReadRule value) {
        return value == null ? null : value.sourceId();
    }

    private boolean isSupportedConditionType(String type) {
        return switch (normalizeConditionType(type)) {
            case "pdc_meta", "pdc_attribute", "lore_regex", "source_id" ->
                true;
            default ->
                false;
        };
    }

    private boolean hasValidConditionEntries(YamlSection configuration, PdcReadRule rule) {
        if (configuration == null || rule == null) {
            return false;
        }
        Object rawEntries = configuration.get("conditions");
        if (rawEntries == null) {
            return true;
        }
        List<Object> entries = ConfigNodes.asObjectList(rawEntries);
        if (entries.isEmpty()) {
            return true;
        }
        return rule.conditions().size() == entries.size();
    }

    private boolean requiresKey(String type) {
        return switch (normalizeConditionType(type)) {
            case "pdc_meta", "pdc_attribute" ->
                true;
            default ->
                false;
        };
    }

    private String normalizeConditionType(String type) {
        return Texts.normalizeId(type);
    }

    private boolean hasUnsupportedChecksField(YamlSection configuration) {
        return configuration != null && configuration.contains("checks");
    }

    private boolean isValidRegex(String pattern) {
        try {
            Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            return true;
        } catch (Exception _) {
            return false;
        }
    }
}
