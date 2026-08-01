package emaki.jiuwu.craft.corelib.action.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Checks the rewritten configs are still loadable YAML and carry no leftover convertible syntax.
 *
 * <p>Parsing matters because the migration edits raw text: a mis-placed quote would produce a file that
 * looks fine in a diff and fails at server start. Uses the same SnakeYAML the server uses.</p>
 *
 * <p>Temporary asset; removed with the rest of the phase 2 test assets.</p>
 */
class LegacyMigratedConfigsValidTest {

    @Test
    @DisplayName("every shipped config still parses as YAML")
    void configsParse() throws IOException {
        List<String> failures = new ArrayList<>();
        int parsed = 0;
        for (Path file : resourceYaml()) {
            try (InputStream input = Files.newInputStream(file)) {
                new Yaml().load(input);
                parsed++;
            } catch (RuntimeException exception) {
                failures.add(file.getFileName() + ": "
                        + exception.getMessage().split(System.lineSeparator())[0]);
            }
        }
        System.out.println("parsed " + parsed + " config file(s)");
        failures.forEach(System.out::println);
        assertEquals(List.of(), failures, "every rewritten config must still parse");
        assertTrue(parsed > 100, "expected the whole corpus, parsed only " + parsed);
    }

    @Test
    @DisplayName("no convertible legacy action ids remain in the shipped configs")
    void noConvertibleLegacyLinesRemain() throws IOException {
        LegacyFileScanner scanner = new LegacyFileScanner();
        List<String> remaining = new ArrayList<>();
        int unconverted = 0;
        for (Path file : resourceYaml()) {
            LegacyFileScanner.Result result = scanner.scan(Files.readString(file));
            for (LegacyFileScanner.Change change : result.changes()) {
                remaining.add(file.getFileName() + " L" + (change.lineIndex() + 1)
                        + ": " + change.oldValue());
            }
            unconverted += result.skips().size();
        }
        remaining.forEach(System.out::println);
        assertEquals(List.of(), remaining, "no convertible legacy line may remain");
        // Now zero: the four nutrition loop lines were migrated by hand to start_task / stop_task once
        // those stages existed, so no legacy action id is left anywhere in the shipped configs.
        assertEquals(0, unconverted, "no unconvertible legacy line may remain either");
    }

    private List<Path> resourceYaml() throws IOException {
        Path root = Path.of("..").toAbsolutePath().normalize();
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
