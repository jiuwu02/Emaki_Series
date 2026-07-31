package emaki.jiuwu.craft.corelib.action.builtin.v2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Checks that every reason key the v2 builtin stages emit exists in both language files.
 *
 * <p>Worth testing because the failure mode is invisible in a build: a stage that reports a key with no entry
 * still compiles and still runs, and the server owner sees a raw key instead of a diagnostic. This scans the
 * stage sources for string literals and the language files for key paths, so it needs no running server.</p>
 *
 * <p>Temporary asset for phase 3 verification.</p>
 */
class BuiltinStageLangKeyTest {

    private static final Pattern REASON_KEY = Pattern.compile("\"(action\\.v2\\.[a-z0-9_.]+)\"");
    private static final Path SOURCE_ROOT = Path.of("src", "main", "java", "emaki", "jiuwu", "craft",
            "corelib", "action", "builtin", "v2");
    private static final Path LANG_ROOT = Path.of("src", "main", "resources", "lang");

    /**
     * Collects the key paths defined by a YAML language file.
     *
     * <p>Deliberately a small indentation walk rather than a YAML parser: the project has no test-scope YAML
     * dependency, and adding one for a key-existence check would be a heavier change than the check deserves.
     * The language files use plain two-space nesting with {@code key: value} lines, which this handles.</p>
     */
    private static Set<String> keysOf(Path file) throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        List<String> stack = new ArrayList<>();
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (raw.isBlank() || raw.stripLeading().startsWith("#")) {
                continue;
            }
            int indent = raw.length() - raw.stripLeading().length();
            String line = raw.strip();
            int colon = colonIndex(line);
            if (colon <= 0) {
                // A wrapped continuation of the previous value; it defines no key.
                continue;
            }
            String name = line.substring(0, colon).strip();
            if (name.isEmpty() || name.contains(" ")) {
                continue;
            }
            int depth = indent / 2;
            while (stack.size() > depth) {
                stack.remove(stack.size() - 1);
            }
            stack.add(name);
            keys.add(String.join(".", stack));
        }
        return keys;
    }

    private static int colonIndex(String line) {
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') {
                quoted = !quoted;
            } else if (current == ':' && !quoted) {
                return index;
            }
        }
        return -1;
    }

    private static Set<String> referencedReasonKeys() throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = REASON_KEY.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    keys.add(matcher.group(1));
                }
            }
        }
        return keys;
    }

    @Test
    void everyReasonKeyExistsInBothLanguageFiles() throws IOException {
        Set<String> referenced = referencedReasonKeys();
        assertFalse(referenced.isEmpty(), "found no reason keys to check; the scan is broken");

        Set<String> zh = keysOf(LANG_ROOT.resolve("zh_CN.yml"));
        Set<String> en = keysOf(LANG_ROOT.resolve("en_US.yml"));

        List<String> missingZh = referenced.stream().filter(key -> !zh.contains(key)).sorted().toList();
        List<String> missingEn = referenced.stream().filter(key -> !en.contains(key)).sorted().toList();

        assertTrue(missingZh.isEmpty(), () -> "keys missing from zh_CN.yml: " + missingZh);
        assertTrue(missingEn.isEmpty(), () -> "keys missing from en_US.yml: " + missingEn);
    }

    @Test
    void theTwoLanguageFilesDefineTheSameKeys() throws IOException {
        Set<String> zh = keysOf(LANG_ROOT.resolve("zh_CN.yml"));
        Set<String> en = keysOf(LANG_ROOT.resolve("en_US.yml"));

        List<String> onlyZh = zh.stream().filter(key -> !en.contains(key)).sorted().toList();
        List<String> onlyEn = en.stream().filter(key -> !zh.contains(key)).sorted().toList();

        assertTrue(onlyZh.isEmpty(), () -> "keys only in zh_CN.yml: " + onlyZh);
        assertTrue(onlyEn.isEmpty(), () -> "keys only in en_US.yml: " + onlyEn);
    }

    @Test
    void languageFilesAreReadable() throws IOException {
        assertNotNull(keysOf(LANG_ROOT.resolve("zh_CN.yml")));
        assertNotNull(keysOf(LANG_ROOT.resolve("en_US.yml")));
    }
}
