package emaki.jiuwu.craft.corelib.script;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class LegacyBundledScriptPolicy {

    private static final Map<String, List<String>> LEGACY_EXTENSION_MARKERS = Map.of(
            "extensions/global/js_broadcast_action.js", List.of("js_broadcast", "executeBroadcast", "registerAction"),
            "extensions/global/js_placeholders.js", List.of("js_online_count", "js_player_world", "registerPlaceholder"),
            "extensions/global/js_event_examples.js", List.of("ENABLE_EXAMPLE_EVENTS", "js_join_logger", "onEvent"),
            "extensions/attribute/js_fire_mastery.js", List.of("js_fire_mastery", "js_fire_mastery_provider", "js_fire_mastery_damage"),
            "extensions/skills/js_lightning_strike.js", List.of("js_lightning_strike", "validateLightning", "executeLightning")
    );

    private LegacyBundledScriptPolicy() {
    }

    public static boolean shouldSkipAutoLoad(Path scriptRoot, String logicalPath) {
        String normalizedPath = normalize(logicalPath);
        List<String> markers = LEGACY_EXTENSION_MARKERS.get(normalizedPath);
        if (scriptRoot == null || markers == null || markers.isEmpty()) {
            return false;
        }
        Path scriptPath = scriptRoot.resolve(normalizedPath).toAbsolutePath().normalize();
        try {
            String content = Files.readString(scriptPath, StandardCharsets.UTF_8);
            return markers.stream().allMatch(content::contains);
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private static String normalize(String path) {
        return Texts.trim(path).replace('\\', '/');
    }
}
