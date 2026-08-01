package emaki.jiuwu.craft.corelib.action.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Applies the migration to this repository's shipped configs.
 *
 * <p>Runs only when {@code -Dlegacy.apply=true}, so an ordinary build never rewrites source files.
 * Writing in place is the point here: these configs ship inside the jars, so they have to carry v2
 * syntax before the old engine is deleted.</p>
 *
 * <p>Deliberately does not use {@link LegacyActionMigrator}'s backup or marker: those exist for a live
 * server, where the original files are the operator's data. Here the originals are in version control,
 * which is a better backup than a sibling file, and a stray marker or {@code .legacy-backup} would be
 * committed by accident.</p>
 *
 * <p>Temporary asset; removed with the rest of the phase 2 test assets.</p>
 */
class LegacyApplyMigrationTest {

    @Test
    @DisplayName("rewrites the shipped configs in place when explicitly asked")
    void applyMigration() throws IOException {
        if (!Boolean.parseBoolean(System.getProperty("legacy.apply", "false"))) {
            System.out.println("skipped: pass -Dlegacy.apply=true to rewrite the shipped configs");
            return;
        }
        LegacyFileScanner scanner = new LegacyFileScanner();
        Path root = Path.of("..").toAbsolutePath().normalize();
        int changedFiles = 0;
        int changedLines = 0;
        int skipped = 0;
        List<String> touched = new ArrayList<>();
        for (Path file : resourceYaml(root)) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            LegacyFileScanner.Result result = scanner.scan(content);
            skipped += result.skips().size();
            if (!result.hasChanges()) {
                continue;
            }
            Files.writeString(file, scanner.rewrite(result), StandardCharsets.UTF_8);
            changedFiles++;
            changedLines += result.changes().size();
            touched.add(root.relativize(file) + " (" + result.changes().size() + " line(s))");
        }
        System.out.println("rewrote " + changedLines + " line(s) in " + changedFiles + " file(s); "
                + skipped + " line(s) left as legacy syntax");
        touched.forEach(entry -> System.out.println("  " + entry));
        assertEquals(121, changedLines, "expected the dry-run's 121 convertible lines");
        assertEquals(4, skipped, "expected the four loop lines to remain untouched");
        assertTrue(changedFiles > 0, "nothing was rewritten");
    }

    private List<Path> resourceYaml(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .filter(path -> path.toString().replace('\\', '/')
                            .contains("/src/main/resources/"))
                    .filter(path -> !path.getParent().getFileName().toString().equals("lang"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }
}
