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

public final class AccessoryPartLoader {

    private static final String FILE_NAME = "parts.yml";

    private final JavaPlugin plugin;
    private final MessageService messageService;
    private final List<String> issues = new ArrayList<>();
    private List<AccessoryPart> parts = List.of();

    public AccessoryPartLoader(JavaPlugin plugin, MessageService messageService) {
        this.plugin = plugin;
        this.messageService = messageService;
    }

    public File file() {
        return new File(plugin.getDataFolder(), FILE_NAME);
    }

    public List<AccessoryPart> parts() {
        return parts;
    }

    public List<String> issues() {
        return List.copyOf(issues);
    }

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
