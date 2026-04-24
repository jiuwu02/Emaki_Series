package emaki.jiuwu.craft.corelib.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Parses and resolves configurable sound definitions.
 */
public final class SoundParser {

    public record SoundDefinition(String key, float volume, float pitch) {

    }

    private SoundParser() {
    }

    public static SoundDefinition parse(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof SoundDefinition definition) {
            return definition;
        }
        if (raw instanceof String text) {
            return parseCompact(text);
        }
        String key = ConfigNodes.string(raw, "sound",
                ConfigNodes.string(raw, "type", ConfigNodes.string(raw, "key", null)));
        if (Texts.isBlank(key)) {
            return null;
        }
        return new SoundDefinition(
                Texts.trim(key),
                clampFloat(ConfigNodes.get(raw, "volume"), 1F),
                clampFloat(ConfigNodes.get(raw, "pitch"), 1F)
        );
    }

    private static SoundDefinition parseCompact(String text) {
        if (Texts.isBlank(text)) {
            return null;
        }
        String trimmed = Texts.trim(text);
        String[] parts = trimmed.split("-");
        if (parts.length == 1) {
            return new SoundDefinition(trimmed, 1F, 1F);
        }
        Float pitch = tryParseTrailingFloat(parts[parts.length - 1]);
        Float volume = parts.length >= 3 ? tryParseTrailingFloat(parts[parts.length - 2]) : null;
        if (pitch == null) {
            return new SoundDefinition(trimmed, 1F, 1F);
        }
        int idPartCount = volume == null ? parts.length - 1 : parts.length - 2;
        if (idPartCount <= 0) {
            return new SoundDefinition(trimmed, 1F, 1F);
        }
        String key = String.join("-", java.util.Arrays.copyOf(parts, idPartCount));
        return new SoundDefinition(
                key,
                volume == null ? 1F : volume,
                pitch
        );
    }

    public static Sound resolve(Object raw) {
        return resolve(parse(raw));
    }

    public static Sound resolve(SoundDefinition definition) {
        if (definition == null || Texts.isBlank(definition.key())) {
            return null;
        }
        for (String candidate : candidates(definition.key())) {
            NamespacedKey key = NamespacedKey.fromString(candidate);
            if (key == null) {
                continue;
            }
            Sound sound = Registry.SOUNDS.get(key);
            if (sound != null) {
                return sound;
            }
        }
        return null;
    }

    private static float clampFloat(Object raw, float fallback) {
        if (raw instanceof Number number) {
            return number.floatValue();
        }
        if (Texts.isBlank(raw)) {
            return fallback;
        }
        try {
            return Float.parseFloat(Texts.toStringSafe(raw));
        } catch (NumberFormatException _) {
            return fallback;
        }
    }

    private static Float tryParseTrailingFloat(String raw) {
        if (Texts.isBlank(raw)) {
            return null;
        }
        try {
            return Float.parseFloat(Texts.toStringSafe(raw));
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private static List<String> candidates(String rawSound) {
        String trimmed = rawSound.trim();
        String lowered = trimmed.toLowerCase(Locale.ROOT);
        String dotted = lowered.replace('_', '.');
        String underscored = lowered.replace('.', '_');
        List<String> candidates = new ArrayList<>();
        addCandidate(candidates, trimmed);
        addCandidate(candidates, lowered);
        addCandidate(candidates, dotted);
        addCandidate(candidates, underscored);
        if (!lowered.contains(":")) {
            addCandidate(candidates, "minecraft:" + lowered);
            addCandidate(candidates, "minecraft:" + dotted);
            addCandidate(candidates, "minecraft:" + underscored);
        }
        return candidates;
    }

    private static void addCandidate(List<String> candidates, String candidate) {
        if (Texts.isBlank(candidate) || candidates.contains(candidate)) {
            return;
        }
        candidates.add(candidate);
    }
}
