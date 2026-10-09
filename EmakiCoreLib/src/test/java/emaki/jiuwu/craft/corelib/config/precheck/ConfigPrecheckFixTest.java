package emaki.jiuwu.craft.corelib.config.precheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity;

final class ConfigPrecheckFixTest {

    private static final class ProbeContributor extends AbstractModuleConfigPrecheckContributor {

        private final List<File> watchedFiles = new ArrayList<>();
        private final List<File> watchedDirectories = new ArrayList<>();

        private ProbeContributor() {
            super("probe");
        }

        @Override
        public ConfigPrecheckResult check(CoreLibConfig config, ConfigPrecheckContext context) {
            List<ConfigPrecheckIssue> issues = new ArrayList<>();
            for (File file : watchedFiles) {
                checkFile(file, file.getName(), issues);
            }
            for (File directory : watchedDirectories) {
                checkDirectory(directory, directory.getName(), issues);
            }
            if (issues.isEmpty()) {
                addSuccessIssue(issues, "probe", message("passed"));
            }
            return new ConfigPrecheckResult(module(), issues);
        }
    }

    @Test
    void missingFileProducesCopyResourceFix(@TempDir Path tempDir) {
        ProbeContributor contributor = new ProbeContributor();
        File missing = tempDir.resolve("config.yml").toFile();
        contributor.watchedFiles.add(missing);

        ConfigPrecheckResult result = contributor.check(CoreLibConfig.defaults(), null);

        assertEquals(1, result.issues().size());
        ConfigPrecheckIssue issue = result.issues().get(0);
        assertEquals(ConfigPrecheckSeverity.ERROR, issue.severity());
        assertNotNull(issue.fix());
        assertEquals(ConfigPrecheckFixAction.Kind.COPY_RESOURCE, issue.fix().kind());
        assertEquals(missing, issue.fix().target());
        assertEquals("config.yml", issue.fix().resource());
        assertFalse(issue.hint().isBlank());
    }

    @Test
    void missingDirectoryProducesCreateDirectoryFix(@TempDir Path tempDir) {
        ProbeContributor contributor = new ProbeContributor();
        File missingDir = tempDir.resolve("mobs").toFile();
        contributor.watchedDirectories.add(missingDir);

        ConfigPrecheckResult result = contributor.check(CoreLibConfig.defaults(), null);

        assertEquals(1, result.issues().size());
        ConfigPrecheckIssue issue = result.issues().get(0);
        assertNotNull(issue.fix());
        assertEquals(ConfigPrecheckFixAction.Kind.CREATE_DIRECTORY, issue.fix().kind());

        assertTrue(contributor.applyFix(issue.fix()));
        assertTrue(missingDir.isDirectory());
    }

    @Test
    void existingFileWithoutMissingKeysProducesNoIssue(@TempDir Path tempDir) throws Exception {
        ProbeContributor contributor = new ProbeContributor();
        File existing = tempDir.resolve("config.yml").toFile();
        Files.writeString(existing.toPath(), "a: 1");
        contributor.watchedFiles.add(existing);

        ConfigPrecheckResult result = contributor.check(CoreLibConfig.defaults(), null);

        assertEquals(1, result.issues().size());
        assertEquals(ConfigPrecheckSeverity.INFO, result.issues().get(0).severity());
    }

    @Test
    void copyResourceFixWithoutPluginFails(@TempDir Path tempDir) {
        ProbeContributor contributor = new ProbeContributor();
        File missing = tempDir.resolve("config.yml").toFile();
        ConfigPrecheckFixAction action =
                new ConfigPrecheckFixAction(ConfigPrecheckFixAction.Kind.COPY_RESOURCE, missing, "config.yml");

        assertFalse(contributor.applyFix(action));
        assertFalse(missing.exists());
    }

    @Test
    void serviceFixCountsAppliedAndRechecks(@TempDir Path tempDir) {
        ProbeContributor contributor = new ProbeContributor();
        File missingDir = tempDir.resolve("gui").toFile();
        contributor.watchedDirectories.add(missingDir);

        ConfigPrecheckService service = new ConfigPrecheckService();
        service.registry().register(contributor);

        ConfigPrecheckFixResult result = service.fix(CoreLibConfig.defaults(), "probe");

        assertEquals(1, result.fixed());
        assertEquals(0, result.failed());
        assertTrue(missingDir.isDirectory());
        assertNotNull(result.report());
        assertTrue(result.report().issues().stream().anyMatch(i -> "probe".equals(i.module())));
    }

    @Test
    void serviceFixUnknownModuleReturnsUnknownReport() {
        ConfigPrecheckService service = new ConfigPrecheckService();

        ConfigPrecheckFixResult result = service.fix(CoreLibConfig.defaults(), "no_such_module");

        assertEquals(0, result.fixed());
        assertEquals(0, result.failed());
        assertTrue(result.report().issues().stream()
                .anyMatch(i -> "no_such_module".equals(i.module())));
    }

    @Test
    void fixActionRecordRejectsNullTarget() {
        assertThrows(NullPointerException.class,
                () -> new ConfigPrecheckFixAction(ConfigPrecheckFixAction.Kind.CREATE_DIRECTORY, null, "gui"));
    }
}
