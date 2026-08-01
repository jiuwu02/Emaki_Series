package emaki.jiuwu.craft.corelib.action.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the scanner against the real shapes found in this repository's example configs.
 *
 * <p>Temporary asset for phase 6 verification; removed with the rest of the phase 2 test assets.</p>
 */
class LegacyFileScannerTest {

    private final LegacyFileScanner scanner = new LegacyFileScanner();

    @Test
    @DisplayName("rewrites a plain action line and keeps indentation")
    void rewritesPlainLine() {
        String yaml = """
                actions:
                  cut:
                    - 'playsound sound=entity.player.attack.strong volume=0.7 pitch=1.3'
                """;
        LegacyFileScanner.Result result = scanner.scan(yaml);
        assertEquals(1, result.changes().size());
        LegacyFileScanner.Change change = result.changes().get(0);
        assertEquals("play_sound sound=entity.player.attack.strong volume=0.7 pitch=1.3",
                change.newValue());
        assertTrue(scanner.rewrite(result).contains(
                "    - \"play_sound sound=entity.player.attack.strong volume=0.7 pitch=1.3\""),
                "indentation must survive, got: " + scanner.rewrite(result));
    }

    @Test
    @DisplayName("keeps a quoted value containing spaces quoted")
    void keepsSpacedValueQuoted() {
        String yaml = """
                actions:
                  - 'sendmessage text="<green>you did it</green>"'
                """;
        LegacyFileScanner.Result result = scanner.scan(yaml);
        assertEquals(1, result.changes().size());
        assertEquals("send_message text=\"<green>you did it</green>\"",
                result.changes().get(0).newValue());
    }

    @Test
    @DisplayName("does not touch lore entries that look like action lines")
    void skipsBlacklistedKeys() {
        String yaml = """
                lore:
                  - 'playsound sound=foo volume=1'
                description:
                  - 'sendmessage text=hi'
                """;
        assertEquals(List.of(), scanner.scan(yaml).changes());
    }

    @Test
    @DisplayName("reports loopsync as unmappable rather than converting it")
    void reportsUnmappable() {
        String yaml = """
                actions:
                  - 'loopsync template=nutrition_decay times=999999 interval=40t key=k mode=replace'
                  - 'cancelloop key=nutrition_overeat:%player_name%'
                """;
        LegacyFileScanner.Result result = scanner.scan(yaml);
        assertEquals(List.of(), result.changes());
        assertEquals(2, result.skips().size());
        assertEquals("loopsync", result.skips().get(0).oldId());
        assertEquals("cancelloop", result.skips().get(1).oldId());
    }

    @Test
    @DisplayName("converts the control prefix into a gate segment")
    void convertsControlPrefix() {
        String yaml = """
                actions:
                  - '@chance=5 sendactionbar text="<gray>combat xp</gray>"'
                """;
        LegacyFileScanner.Result result = scanner.scan(yaml);
        assertEquals(1, result.changes().size());
        assertEquals("chance 5 | send_action_bar text=\"<gray>combat xp</gray>\"",
                result.changes().get(0).newValue());
    }

    @Test
    @DisplayName("rewrites only the seven Emaki-owned player placeholders")
    void rewritesOwnedPlaceholdersOnly() {
        String yaml = """
                actions:
                  - 'sendmessage text=%player_name%'
                  - 'sendmessage text=%player_ping%'
                """;
        LegacyFileScanner.Result result = scanner.scan(yaml);
        assertEquals(2, result.changes().size());
        assertEquals("send_message text=%caster.name%", result.changes().get(0).newValue());
        assertEquals("send_message text=%player_ping%", result.changes().get(1).newValue(),
                "PAPI placeholders must survive untouched");
    }

    @Test
    @DisplayName("does not treat a mapping entry as an action line")
    void skipsMappingEntries() {
        String yaml = """
                combo:
                  - id: overeat
                    required_count: 5
                """;
        assertEquals(List.of(), scanner.scan(yaml).changes());
    }

    @Test
    @DisplayName("does not treat a bare enum value as an action")
    void skipsBareEnumValues() {
        // Regression: `PROJECTILE` in an event list parsed as the `projectile` action because the old
        // parser lowercases ids, and was rewritten to `projectile`, corrupting an unrelated config.
        String yaml = """
                allowed_events:
                  - PROJECTILE
                some_other_list:
                  - PROJECTILE
                  - damage
                """;
        assertEquals(List.of(), scanner.scan(yaml).changes());
    }

    @Test
    @DisplayName("preserves the author's quoting instead of re-deriving it")
    void preservesAuthorQuoting() {
        // Regression: quotes the author wrote were dropped whenever the value had no space, which
        // enlarged the review diff and changed YAML meaning for values containing a colon.
        String yaml = """
                actions:
                  - 'sendmessage text="<green>done</green>"'
                  - 'playsound sound=block.anvil.use volume=1.0 pitch=1.1'
                """;
        LegacyFileScanner.Result result = scanner.scan(yaml);
        assertEquals("send_message text=\"<green>done</green>\"",
                result.changes().get(0).newValue());
        assertEquals("play_sound sound=block.anvil.use volume=1.0 pitch=1.1",
                result.changes().get(1).newValue(),
                "an unquoted value must stay unquoted");
    }

    @Test
    @DisplayName("keeps a value containing a pipe quoted so the lexer cannot split it")
    void quotesPipeBearingValues() {
        String yaml = """
                actions:
                  - 'sendmessage text=a|b'
                """;
        LegacyFileScanner.Result result = scanner.scan(yaml);
        assertEquals("send_message text=\"a|b\"", result.changes().get(0).newValue());
    }

    @Test
    @DisplayName("leaves an already converted pipeline line alone")
    void skipsAlreadyConverted() {
        String yaml = """
                cast:
                  - "self | play_sound sound=entity.blaze.shoot volume=1"
                  - "looking_at range=18 | keep"
                """;
        assertEquals(List.of(), scanner.scan(yaml).changes());
    }
}
