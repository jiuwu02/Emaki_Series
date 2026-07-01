package emaki.jiuwu.craft.codex.advancement.loader;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.codex.advancement.model.AdvancementDefinition;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementFrame;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementPage;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

/**
 * Loads advancement pages from {@code advancements/*.yml}. Each file defines one page
 * (a vanilla advancement tab) with a root advancement and any number of child nodes.
 */
public final class AdvancementPageLoader extends YamlDirectoryLoader<AdvancementPage> {

    public AdvancementPageLoader(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "advancements";
    }

    @Override
    protected String typeName() {
        return "advancement page";
    }

    @Override
    protected AdvancementPage parse(File file, YamlSection configuration) {
        if (configuration == null) {
            return null;
        }
        String pageId = configuration.getString("page_id", "");
        if (Texts.isBlank(pageId)) {
            return null;
        }
        String title = configuration.getString("title", pageId);
        String background = configuration.getString("background", "minecraft:textures/gui/advancements/backgrounds/stone.png");
        String rootId = configuration.getString("root", "");

        Map<String, AdvancementDefinition> definitions = new LinkedHashMap<>();
        YamlSection advancementsSection = configuration.getSection("advancements");
        if (advancementsSection != null) {
            for (String localId : advancementsSection.getKeys(false)) {
                YamlSection node = advancementsSection.getSection(localId);
                if (node == null) {
                    continue;
                }
                definitions.put(localId, parseDefinition(localId, node));
            }
        }
        return new AdvancementPage(pageId.trim(), title, background, rootId.trim(), definitions);
    }

    private AdvancementDefinition parseDefinition(String localId, YamlSection node) {
        String icon = node.getString("icon", "minecraft-book");
        String title = node.getString("title", localId);
        String description = node.getString("description", "");
        AdvancementFrame frame = AdvancementFrame.fromText(node.getString("frame", "task"));
        double x = doubleOf(node, "x", 0.0D);
        double y = doubleOf(node, "y", 0.0D);
        String parent = node.getString("parent", "");
        boolean hidden = Boolean.TRUE.equals(node.getBoolean("hidden", false));
        boolean toast = Boolean.TRUE.equals(node.getBoolean("toast", true));
        boolean announce = Boolean.TRUE.equals(node.getBoolean("announce", false));
        return new AdvancementDefinition(localId, icon, title, description, frame,
                x, y, parent, hidden, toast, announce, node.getStringList("on_complete"));
    }

    private double doubleOf(YamlSection node, String path, double fallback) {
        Double value = node.getDouble(path, fallback);
        return value == null ? fallback : value;
    }

    @Override
    protected String idOf(AdvancementPage value) {
        return value == null ? null : value.pageId();
    }
}
