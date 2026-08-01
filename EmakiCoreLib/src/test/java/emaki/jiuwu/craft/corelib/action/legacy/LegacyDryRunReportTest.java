package emaki.jiuwu.craft.corelib.action.legacy;

import static org.junit.jupiter.api.Assertions.assertFalse;

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
 * Prints the full dry-run diff over this repository's shipped configs.
 *
 * <p>Phase 6 of the plan makes reviewing this diff line by line a mandatory gate before any write, so
 * this exists to produce that diff from the real converter rather than from a reimplementation.
 * Temporary asset; removed with the rest of the phase 2 test assets.</p>
 */
class LegacyDryRunReportTest {

    @Test
    @DisplayName("prints every line the converter would change across the repository")
    void printDryRun() throws IOException {
        Path root = Path.of("..").toAbsolutePath().normalize();
        LegacyFileScanner scanner = new LegacyFileScanner();
        List<Path> files = resourceYaml(root);
        int changed = 0;
        int lines = 0;
        int skipped = 0;
        List<String> report = new ArrayList<>();
        for (Path file : files) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            LegacyFileScanner.Result result = scanner.scan(content);
            if (result.changes().isEmpty() && result.skips().isEmpty()) {
                continue;
            }
            report.add("=== " + root.relativize(file));
            for (LegacyFileScanner.Change change : result.changes()) {
                report.add("  L" + (change.lineIndex() + 1) + " [" + change.parentKey() + "]");
                report.add("    - " + change.oldValue());
                report.add("    + " + change.newValue());
                lines++;
            }
            for (LegacyFileScanner.Skip skip : result.skips()) {
                report.add("  L" + skip.lineNumber() + " SKIP " + skip.oldId()
                        + " (" + skip.reason() + ")");
                report.add("    ! " + skip.value());
                skipped++;
            }
            if (!result.changes().isEmpty()) {
                changed++;
            }
        }
        System.out.println("scanned " + files.size() + " yml files");
        System.out.println("would change " + lines + " line(s) in " + changed + " file(s), "
                + skipped + " skipped");
        report.forEach(System.out::println);
        assertFalse(files.isEmpty(), "expected to find shipped configs under " + root);
    }

    private List<Path> resourceYaml(Path root) throws IOException {
        // No depth limit: Cooking's recipes live under recipes/<station>/, one level deeper than every
        // other module's configs, and a bounded walk silently omitted all 27 of their action lines.
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
