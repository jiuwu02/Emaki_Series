package emaki.jiuwu.craft.accessory.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.accessory.model.AccessoryPart;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlLoadException;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.service.MessageService;

/**
 * Loads accessory part definitions from a single {@code parts.yml}.
 *
 * <p>A flat file rather than a directory: parts are a short list whose declaration order decides GUI
 * ordering, and one file keeps that order visible in one place. Slot instance expansion and cross-part
 * id collision checks belong to the registry, not here - this loader only reports per-part syntax
 * problems.
 *
 * <p>An illegal part id is rejected with a warning instead of being silently repaired. Silently
 * rewriting {@code Ring 2} into {@code ring_2} would produce a part whose slot instance ids collide
 * with the second slot of a {@code ring} part, and the owner would have no idea why.
 */
public final class AccessoryPartLoader {

    private static final String FILE_NAME = "parts.yml";

    private final JavaPlugin plugin;
    private final MessageService messageService;
    private final List<String> issues = new ArrayList<>();
    private List<AccessoryPart> parts = List.of();

    /**
     * Creates the loader.
     *
     * @param plugin         the owning plugin
     * @param messageService message service used for localized warnings; may be {@code null}
     */
    public AccessoryPartLoader(JavaPlugin plugin, MessageService messageService) {
        this.plugin = plugin;
        this.messageService = messageService;
    }

    /** {@return the file backing this loader} */
    public File file() {
        return new File(plugin.getDataFolder(), FILE_NAME);
    }

    /** {@return the parts parsed by the last {@link #load()}, in declaration order} */
    public List<AccessoryPart> parts() {
        return parts;
    }

    /** {@return the issues recorded by the last {@link #load()}} */
    public List<String> issues() {
        return List.copyOf(issues);
    }

    /**
     * Reloads {@code parts.yml}.
     *
     * <p>Blocking IO: call it from a reload path, never from a click or a periodic task.
     *
     * @return how many parts were accepted
     */
    public int load() {
        issues.clear();
        List<AccessoryPart> parsed = new ArrayList<>();
        File file = file();
        YamlSection root;
        try {
            root = YamlFiles.load(file);
        } catch (YamlLoadException exception) {
            warn("accessory.parts_load_failed", Map.of(
                    "file", FILE_NAME,
                    "error", Texts.toStringSafe(exception.getMessage())
            ));
            parts = List.of();
            return 0;
        }
        YamlSection section = root == null ? null : root.getSection("parts");
        if (section == null) {
            warn("accessory.parts_section_missing", Map.of("file", FILE_NAME));
            parts = List.of();
            return 0;
        }
        for (String key : section.getKeys(false)) {
            YamlSection entry = section.getSection(key);
            if (entry == null) {
                continue;
            }
            if (!AccessoryPart.isLegalPartId(key)) {
                warn("accessory.part_id_illegal", Map.of("part", Texts.toStringSafe(key)));
                continue;
            }
            Integer count = entry.getInt("count", 1);
            int resolvedCount = count == null ? 1 : count;
            if (resolvedCount < 1) {
                warn("accessory.part_count_invalid", Map.of(
                        "part", Texts.toStringSafe(key),
                        "count", Integer.toString(resolvedCount)
                ));
                continue;
            }
            parsed.add(new AccessoryPart(
                    key,
                    resolvedCount,
                    entry.getString("display_name", ""),
                    entry.getString("icon", "")
            ));
        }
        parts = List.copyOf(parsed);
        return parts.size();
    }

    private void warn(String key, Map<String, ?> replacements) {
        if (messageService != null) {
            issues.add(messageService.message(key, replacements));
            messageService.warning(key, replacements);
            return;
        }
        issues.add(key + " " + replacements);
        plugin.getLogger().warning(key + " " + replacements);
    }
}
