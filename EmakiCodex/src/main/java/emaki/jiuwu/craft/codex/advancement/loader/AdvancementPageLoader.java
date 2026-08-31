package emaki.jiuwu.craft.codex.advancement.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementDefinition;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementFrame;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementPage;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementTrigger;
import emaki.jiuwu.craft.corelib.condition.ConditionGroup;
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public final class AdvancementPageLoader extends YamlDirectoryLoader<AdvancementPage> {

    private final EmakiCodexPlugin codexPlugin;

    public AdvancementPageLoader(EmakiCodexPlugin plugin) {
        super(plugin);
        this.codexPlugin = plugin;
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
        boolean announce = Boolean.TRUE.equals(
                node.getBoolean("announce", codexPlugin.appConfig().announceDefault()));
        return new AdvancementDefinition(localId, icon, title, description, frame,
                x, y, parent, hidden, toast, announce,
                parseCompleteActions(node), parseTriggers(node));
    }

    private List<String> parseCompleteActions(YamlSection node) {
        if (node == null) {
            return List.of();
        }
        return List.copyOf(node.getStringList("actions.complete"));
    }

    private List<AdvancementTrigger> parseTriggers(YamlSection node) {
        Object raw = node == null ? null : node.get("triggers.entries");
        if (raw == null) {
            return List.of();
        }
        List<AdvancementTrigger> triggers = new ArrayList<>();
        for (Object rawEntry : ConfigNodes.asObjectList(raw)) {
            Map<String, Object> entry = ConfigNodes.entries(rawEntry);
            String event = Texts.toStringSafe(entry.get("event"));
            if (Texts.isBlank(event)) {
                continue;
            }
            ConditionGroup condition = ConditionGroup.fromConfig(entry.get("condition"));
            triggers.add(new AdvancementTrigger(event, condition));
        }
        return List.copyOf(triggers);
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
