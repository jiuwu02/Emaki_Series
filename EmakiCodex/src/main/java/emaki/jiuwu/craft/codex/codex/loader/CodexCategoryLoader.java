package emaki.jiuwu.craft.codex.codex.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementTrigger;
import emaki.jiuwu.craft.codex.codex.model.CodexCategory;
import emaki.jiuwu.craft.codex.codex.model.CodexEntry;
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.condition.ConditionGroup;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;

public final class CodexCategoryLoader extends YamlDirectoryLoader<CodexCategory> {

    private static final String DEFAULT_ICON = "minecraft-book";
    private static final int DEFAULT_ORDER = 100;

    public CodexCategoryLoader(EmakiCodexPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "codex";
    }

    @Override
    protected String typeName() {
        return "codex category";
    }

    @Override
    protected CodexCategory parse(File file, YamlSection configuration) {
        if (configuration == null) {
            return null;
        }
        String categoryId = Texts.normalizeId(configuration.getString("category_id", ""));
        if (Texts.isBlank(categoryId)) {
            return null;
        }
        String title = configuration.getString("title", categoryId);
        String icon = configuration.getString("icon", DEFAULT_ICON);
        Integer order = configuration.getInt("order", DEFAULT_ORDER);

        Map<String, CodexEntry> entries = new LinkedHashMap<>();
        YamlSection entriesSection = configuration.getSection("entries");
        if (entriesSection != null) {
            for (String rawEntryId : entriesSection.getKeys(false)) {
                YamlSection node = entriesSection.getSection(rawEntryId);
                String entryId = Texts.normalizeId(rawEntryId);
                if (node == null || Texts.isBlank(entryId)) {
                    issue("codex.loader.entry_skipped", Map.of(
                            "file", file == null ? "" : file.getName(),
                            "entry", Texts.toStringSafe(rawEntryId)));
                    continue;
                }
                entries.put(entryId, parseEntry(entryId, node));
            }
        }
        if (entries.isEmpty()) {
            issue("codex.loader.category_empty", Map.of(
                    "file", file == null ? "" : file.getName(),
                    "category", categoryId));
        }
        return new CodexCategory(categoryId, title, icon,
                order == null ? DEFAULT_ORDER : order, entries);
    }

    private CodexEntry parseEntry(String entryId, YamlSection node) {
        String title = node.getString("title", entryId);
        String description = node.getString("description", "");
        String icon = node.getString("icon", DEFAULT_ICON);
        boolean hidden = Boolean.TRUE.equals(node.getBoolean("hidden", false));
        YamlSection unlock = node.getSection("unlock");
        YamlSection rewards = node.getSection("rewards");
        return new CodexEntry(entryId, title, description, icon, hidden,
                parseTriggers(unlock), parseUnlockAdvancements(unlock),
                parseAttributeRewards(rewards), parseClaimActions(rewards));
    }

    private List<AdvancementTrigger> parseTriggers(YamlSection unlock) {
        Object raw = unlock == null ? null : unlock.get("triggers");
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

    private List<String> parseUnlockAdvancements(YamlSection unlock) {
        if (unlock == null) {
            return List.of();
        }
        List<String> advancements = new ArrayList<>();
        for (String raw : unlock.getStringList("advancements")) {
            String trimmed = Texts.trim(raw);
            if (!trimmed.isEmpty() && !advancements.contains(trimmed)) {
                advancements.add(trimmed);
            }
        }
        return List.copyOf(advancements);
    }

    private Map<String, Double> parseAttributeRewards(YamlSection rewards) {
        YamlSection attributes = rewards == null ? null : rewards.getSection("attributes");
        if (attributes == null) {
            return Map.of();
        }
        Map<String, Double> values = new LinkedHashMap<>();
        for (String rawId : attributes.getKeys(false)) {
            String attributeId = Texts.normalizeId(rawId);
            Double value = attributes.getDouble(rawId, null);
            if (Texts.isBlank(attributeId) || value == null || value == 0.0D) {
                continue;
            }
            values.merge(attributeId, value, Double::sum);
        }
        return Map.copyOf(values);
    }

    private List<String> parseClaimActions(YamlSection rewards) {
        if (rewards == null) {
            return List.of();
        }
        return List.copyOf(rewards.getStringList("actions"));
    }

    @Override
    protected String idOf(CodexCategory value) {
        return value == null ? null : value.categoryId();
    }

    public List<CodexCategory> orderedCategories() {
        List<CodexCategory> categories = new ArrayList<>(all().values());
        categories.sort(Comparator.comparingInt(CodexCategory::order)
                .thenComparing(CodexCategory::categoryId));
        return List.copyOf(categories);
    }

    public CodexEntry entryAt(String categoryId, String entryId) {
        CodexCategory category = get(Texts.normalizeId(categoryId));
        return category == null ? null : category.entry(Texts.normalizeId(entryId));
    }
}
