package emaki.jiuwu.craft.corelib.config.precheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.action.ActionLineParser;
import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

class ConfigPrecheckLocalizationTest {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([A-Za-z0-9_]+)%");
    private static final List<String> COMMON_PRECHECK_KEYS = List.of(
            "console.config_precheck.severity.fatal",
            "console.config_precheck.severity.error",
            "console.config_precheck.severity.warn",
            "console.config_precheck.severity.info",
            "console.config_precheck.hint",
            "console.config_precheck.messages.passed",
            "console.config_precheck.messages.required_file_missing",
            "console.config_precheck.messages.path_not_file",
            "console.config_precheck.messages.file_not_readable",
            "console.config_precheck.messages.required_directory_missing",
            "console.config_precheck.messages.path_not_directory",
            "console.config_precheck.messages.directory_not_readable"
    );

    @TempDir
    Path temporaryDirectory;

    @Test
    void commonFileAndDirectoryIssuesUseOwnerMessages() throws Exception {
        FakeMessages messages = new FakeMessages(Map.of(
                "console.config_precheck.messages.required_file_missing", "owner:file-missing",
                "console.config_precheck.messages.path_not_directory", "owner:not-directory"
        ));
        File nonDirectory = Files.createFile(temporaryDirectory.resolve("file.yml")).toFile();
        TestContributor contributor = new TestContributor(messages, temporaryDirectory.resolve("missing.yml").toFile(), nonDirectory);

        ConfigPrecheckResult result = contributor.check(CoreLibConfig.defaults(), new ConfigPrecheckContext(null, null, null));

        assertEquals(List.of("owner:file-missing", "owner:not-directory"),
                result.issues().stream().map(ConfigPrecheckIssue::message).toList());
    }

    @Test
    void coreLibDynamicTemplateParameterIsLocalized() {
        FakeMessages messages = new FakeMessages(Map.of(
                "console.config_precheck.messages.template_missing_reference", "missing-template=%template%"
        ));
        CoreLibConfig defaults = CoreLibConfig.defaults();
        CoreLibConfig config = new CoreLibConfig(
                defaults.language(),
                defaults.releaseDefaultData(),
                Map.of("owner", List.of("usetemplate name=ghost")),
                defaults.loopConfig(),
                defaults.scriptConfig(),
                defaults.webConsoleConfig(),
                defaults.guiConfig(),
                defaults.gameplayEventConfig()
        );
        CoreLibConfigPrecheckContributor contributor = new CoreLibConfigPrecheckContributor(() -> messages);

        ConfigPrecheckResult result = contributor.check(
                config,
                new ConfigPrecheckContext(new ActionLineParser(), new ActionRegistry(), null)
        );

        assertEquals(1, result.issues().size());
        assertEquals("missing-template=ghost", result.issues().getFirst().message());
    }

    @Test
    void severityAndHintFormattingAreLocalizedWithoutChangingBlockingDecision() {
        FakeMessages messages = new FakeMessages(Map.of(
                "console.config_precheck_passed", "PASS %issues%",
                "console.config_precheck_failed", "FAIL %issues%",
                "console.config_precheck_issue", "[%severity%] %module%:%path% %message%%hint%",
                "console.config_precheck.severity.warn", "警告",
                "console.config_precheck.severity.error", "错误",
                "console.config_precheck.hint", "《%hint%》"
        ));
        ConfigPrecheckIssue warning = new ConfigPrecheckIssue(
                "owner",
                "config.yml",
                ConfigPrecheckSeverity.WARN,
                "warning-message",
                "localized-hint"
        );
        ConfigPrecheckReport warningReport = report(warning);

        assertTrue(warningReport.success());
        List<String> warningLines = warningReport.formatLines(messages, "owner");
        assertEquals("PASS 1", warningLines.getFirst());
        assertEquals("[警告] owner:config.yml warning-message《localized-hint》", warningLines.get(1));
        assertFalse(warningLines.get(1).contains("WARN"));
        assertTrue(warningReport.success());

        ConfigPrecheckReport errorReport = report(ConfigPrecheckIssue.of(
                "owner",
                "config.yml",
                ConfigPrecheckSeverity.ERROR,
                "error-message"
        ));
        assertFalse(errorReport.success());
        assertEquals("FAIL 1", errorReport.formatLines(messages, "owner").getFirst());
        assertFalse(errorReport.success());
    }

    @Test
    void bundledLanguageFilesParseWithAlignedPrecheckAndLoaderKeys() {
        Path projectRoot = projectRoot();
        Map<String, String> expectedVersions = Map.of(
                "EmakiCoreLib", "4.5.3",
                "EmakiAttribute", "4.5.4",
                "EmakiForge", "4.5.1",
                "EmakiGem", "2.5.1",
                "EmakiSkills", "2.5.2",
                "EmakiStrengthen", "4.5.2",
                "EmakiItem", "2.5.2",
                "EmakiCooking", "4.0.1",
                "EmakiLevel", "1.3.2"
        );

        for (Map.Entry<String, String> entry : expectedVersions.entrySet()) {
            String module = entry.getKey();
            Path resources = projectRoot.resolve(module).resolve("src/main/resources");
            YamlSection config = YamlFiles.load(resources.resolve("config.yml").toFile());
            YamlSection chinese = YamlFiles.load(resources.resolve("lang/zh_CN.yml").toFile());
            YamlSection english = YamlFiles.load(resources.resolve("lang/en_US.yml").toFile());

            assertEquals(entry.getValue(), config.getString("version"), module + " config version");
            assertEquals(entry.getValue(), chinese.getString("version"), module + " zh_CN version");
            assertEquals(entry.getValue(), english.getString("version"), module + " en_US version");
            for (String key : COMMON_PRECHECK_KEYS) {
                assertTrue(chinese.isString(key), module + " zh_CN missing " + key);
                assertTrue(english.isString(key), module + " en_US missing " + key);
            }
            assertAlignedPrefix(module, chinese, english, "console.config_precheck");
            assertAlignedPrefix(module, chinese, english, "loader");
        }
    }

    private static Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("EmakiCoreLib"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("EmakiCoreLib"))) {
            return parent;
        }
        throw new IllegalStateException("Unable to locate the Emaki multi-module project root from " + current);
    }

    private static void assertAlignedPrefix(String module,
            YamlSection chinese,
            YamlSection english,
            String prefix) {
        Set<String> chineseKeys = keysUnder(chinese, prefix);
        Set<String> englishKeys = keysUnder(english, prefix);
        assertEquals(chineseKeys, englishKeys, module + " language keys under " + prefix);
        for (String key : chineseKeys) {
            if (!chinese.isString(key) && !english.isString(key)) {
                continue;
            }
            assertTrue(chinese.isString(key), module + " zh_CN value is not text: " + key);
            assertTrue(english.isString(key), module + " en_US value is not text: " + key);
            assertEquals(placeholders(chinese.getString(key)), placeholders(english.getString(key)),
                    module + " placeholder mismatch: " + key);
        }
    }

    private static Set<String> keysUnder(YamlSection section, String prefix) {
        Set<String> keys = new LinkedHashSet<>();
        for (String key : section.getKeys(true)) {
            if (key.equals(prefix) || key.startsWith(prefix + ".")) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static Set<String> placeholders(String text) {
        Set<String> placeholders = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(Texts.toStringSafe(text));
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        return placeholders;
    }

    private static ConfigPrecheckReport report(ConfigPrecheckIssue issue) {
        return new ConfigPrecheckReport(
                Instant.EPOCH,
                List.of(new ConfigPrecheckResult(issue.module(), List.of(issue)))
        );
    }

    private static final class TestContributor extends AbstractModuleConfigPrecheckContributor {

        private final File missingFile;
        private final File nonDirectory;

        private TestContributor(LogMessages messages, File missingFile, File nonDirectory) {
            super("owner", () -> messages);
            this.missingFile = missingFile;
            this.nonDirectory = nonDirectory;
        }

        @Override
        public ConfigPrecheckResult check(CoreLibConfig config, ConfigPrecheckContext context) {
            List<ConfigPrecheckIssue> issues = new ArrayList<>();
            checkFile(missingFile, "missing.yml", issues);
            checkDirectory(nonDirectory, "not-a-directory", issues);
            return new ConfigPrecheckResult(module(), issues);
        }
    }

    private static final class FakeMessages implements LogMessages {

        private final Map<String, String> messages;

        private FakeMessages(Map<String, String> messages) {
            this.messages = messages;
        }

        @Override
        public String message(String key) {
            return messages.getOrDefault(key, key);
        }

        @Override
        public String message(String key, Map<String, ?> replacements) {
            return Texts.formatTemplate(message(key), replacements == null ? Map.of() : replacements);
        }

        @Override
        public void info(String key) {
        }

        @Override
        public void info(String key, Map<String, ?> replacements) {
        }

        @Override
        public void warning(String key) {
        }

        @Override
        public void warning(String key, Map<String, ?> replacements) {
        }

        @Override
        public void severe(String key) {
        }

        @Override
        public void severe(String key, Map<String, ?> replacements) {
        }
    }
}
