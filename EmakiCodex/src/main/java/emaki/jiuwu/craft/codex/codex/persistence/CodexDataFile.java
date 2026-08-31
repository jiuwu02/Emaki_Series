package emaki.jiuwu.craft.codex.codex.persistence;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import emaki.jiuwu.craft.codex.codex.model.CodexEntryState;
import emaki.jiuwu.craft.codex.codex.model.PlayerCodex;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public final class CodexDataFile {

    public static final int FORMAT_VERSION = 1;

    private static final String KEY_FORMAT_VERSION = "format_version";
    private static final String KEY_PLAYER_NAME = "player_name";
    private static final String KEY_ENTRIES = "entries";
    private static final String KEY_UNLOCKED_AT = "unlocked_at";
    private static final String KEY_ACTIVATED = "activated";
    private static final String KEY_CLAIMED = "claimed";

    private final Logger logger;
    private final Path dataRoot;

    public CodexDataFile(Logger logger, Path dataRoot) {
        this.logger = logger;
        this.dataRoot = dataRoot;
    }

    public File fileFor(UUID playerId) {
        return dataRoot.resolve(playerId + ".yml").toFile();
    }

    public Path dataRoot() {
        return dataRoot;
    }

    public PlayerCodex read(UUID playerId, String playerName, YamlSection root) {
        PlayerCodex codex = new PlayerCodex(playerId);
        Map<String, CodexEntryState> entries = new LinkedHashMap<>();
        String storedName = "";
        if (root != null && !root.isEmpty()) {
            YamlSection entriesSection = root.getSection(KEY_ENTRIES);
            if (entriesSection != null) {
                for (String key : entriesSection.getKeys(false)) {
                    readEntry(playerId, entriesSection, key, entries);
                }
            }
            storedName = root.getString(KEY_PLAYER_NAME, "");
        }
        codex.installLoaded(entries);
        codex.playerName(Texts.isNotBlank(storedName) ? storedName : playerName);
        codex.clearDirty();
        return codex;
    }

    private void readEntry(UUID playerId,
            YamlSection entriesSection,
            String rawKey,
            Map<String, CodexEntryState> entries) {
        String key = Texts.trim(rawKey);
        YamlSection node = entriesSection.getSection(rawKey);
        if (Texts.isBlank(key) || node == null) {
            warn("Dropped unreadable codex entry '" + Texts.toStringSafe(rawKey)
                    + "' for player " + playerId);
            return;
        }
        Long unlockedAt = longOf(node.get(KEY_UNLOCKED_AT));
        if (unlockedAt == null) {
            warn("Dropped codex entry '" + key + "' without a valid unlocked_at for player " + playerId);
            return;
        }
        boolean activated = Boolean.TRUE.equals(node.getBoolean(KEY_ACTIVATED, false));
        boolean claimed = Boolean.TRUE.equals(node.getBoolean(KEY_CLAIMED, false));
        entries.put(key, new CodexEntryState(unlockedAt, activated, claimed));
    }

    public Map<String, Object> write(PlayerCodex codex) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(KEY_FORMAT_VERSION, FORMAT_VERSION);
        root.put(KEY_PLAYER_NAME, codex == null ? "" : codex.playerName());
        Map<String, Object> entries = new LinkedHashMap<>();
        if (codex != null) {
            codex.entries().forEach((key, state) -> {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put(KEY_UNLOCKED_AT, state.unlockedAt());
                node.put(KEY_ACTIVATED, state.activated());
                node.put(KEY_CLAIMED, state.claimed());
                entries.put(key, node);
            });
        }
        root.put(KEY_ENTRIES, entries);
        return root;
    }

    private Long longOf(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        String text = Texts.trim(raw);
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException exception) {
            warn("Unparseable codex timestamp '" + text + "': " + exception.getMessage());
            return null;
        }
    }

    private void warn(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }
}
