package emaki.jiuwu.craft.attribute.loader;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.PdcReadRule;
import emaki.jiuwu.craft.attribute.model.PdcReadRule.RuleCheck;
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
        for (RuleCheck check : rule.checks()) {
            if (!isSupportedCheckType(check.type())) {
                issue(
                        "loader.schema_invalid_enum",
                        Map.of(
                                "type", typeName(),
                                "file", file.getName(),
                                "field", "checks.type"
                        )
                );
                return false;
            }
            if (requiresKey(check.type()) && Texts.isBlank(check.key())) {
                issue(
                        "loader.schema_missing_id",
                        Map.of(
                                "type", typeName(),
                                "file", file.getName(),
                                "field", "checks.key"
                        )
                );
                return false;
            }
            if ("lore_regex".equals(normalizeCheckType(check.type())) && Texts.isBlank(check.pattern())) {
                issue(
                        "loader.schema_missing_section",
                        Map.of(
                                "type", typeName(),
                                "file", file.getName(),
                                "field", "checks.pattern"
                        )
                );
                return false;
            }
            if (Texts.isNotBlank(check.pattern()) && !isValidRegex(check.pattern())) {
                issue(
                        "loader.load_failed",
                        Map.of(
                                "type", typeName(),
                                "file", file.getName(),
                                "error", "无效正则: " + check.pattern()
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

    private boolean isSupportedCheckType(String type) {
        return switch (normalizeCheckType(type)) {
            case "pdc_meta", "pdc_attribute", "lore_regex", "source_id" ->
                true;
            default ->
                false;
        };
    }

    private boolean requiresKey(String type) {
        return switch (normalizeCheckType(type)) {
            case "pdc_meta", "pdc_attribute" ->
                true;
            default ->
                false;
        };
    }

    private String normalizeCheckType(String type) {
        String normalized = Texts.lower(type);
        return switch (normalized) {
            case "meta" ->
                "pdc_meta";
            case "attribute", "attr" ->
                "pdc_attribute";
            case "lore" ->
                "lore_regex";
            case "source" ->
                "source_id";
            default ->
                normalized;
        };
    }

    private boolean isValidRegex(String pattern) {
        try {
            Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
