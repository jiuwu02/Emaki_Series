package emaki.jiuwu.craft.gem.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;

public final class GemItemFactory {

    private static final PdcService PDC = new PdcService("emaki");
    private static final PdcPartition GEM_ITEM_PARTITION = PDC.partition("gem.item");

    private final EmakiGemPlugin plugin;
    private final ItemSourceService itemSourceService;

    public GemItemFactory(EmakiGemPlugin plugin, ItemSourceService itemSourceService) {
        this.plugin = plugin;
        this.itemSourceService = itemSourceService;
    }

    public ItemStack createGemItem(GemDefinition definition, int level, int amount) {
        if (definition == null || definition.itemSource() == null) {
            return null;
        }
        ItemStack itemStack = createBaseItem(definition.itemSource(), amount);
        if (itemStack == null) {
            return null;
        }
        PDC.set(itemStack, GEM_ITEM_PARTITION, "id", PersistentDataType.STRING, definition.id());
        PDC.set(itemStack, GEM_ITEM_PARTITION, "level", PersistentDataType.INTEGER, Math.max(1, level));
        PDC.set(itemStack, GEM_ITEM_PARTITION, "updated_at", PersistentDataType.LONG, System.currentTimeMillis());
        return itemStack;
    }

    public ItemStack recreateGemItem(GemItemInstance instance, int amount) {
        if (instance == null) {
            return null;
        }
        return createGemItem(plugin.gemLoader().get(instance.gemId()), instance.level(), amount);
    }

    public Map<String, Object> gemPlaceholders(GemDefinition definition, int level, Integer oldLevel) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("gem_id", definition == null ? "" : definition.id());
        placeholders.put("display_name", resolveGemDisplayName(definition, level));
        placeholders.put("old_display_name", resolveGemDisplayName(definition, oldLevel == null ? level : oldLevel));
        placeholders.put("current_level", level);
        placeholders.put("target_level", level);
        placeholders.put("tier", definition == null ? 1 : definition.tier());
        placeholders.put("gem_type", definition == null ? "universal" : definition.gemType());
        if (definition != null) {
            definition.statsForLevel(level).forEach((key, value) -> placeholders.put(key, Numbers.formatNumber(value, plugin.appConfig().numberFormat())));
        }
        return placeholders;
    }

    public String resolveGemDisplayName(GemDefinition definition, int level) {
        if (definition == null) {
            return "";
        }
        String configuredDisplayName = "";
        GemDefinition.GemUpgradeLevel upgradeLevel = definition.upgradeLevel(level);
        if (upgradeLevel != null && Texts.isNotBlank(upgradeLevel.displayName())) {
            configuredDisplayName = upgradeLevel.displayName();
        } else if (Texts.isNotBlank(definition.displayName())
                && !definition.displayName().equalsIgnoreCase(definition.id())) {
            configuredDisplayName = definition.displayName();
        }
        if (Texts.isNotBlank(configuredDisplayName)) {
            return configuredDisplayName;
        }
        ItemStack previewItem = createBaseItem(definition.itemSource(), 1);
        if (previewItem != null) {
            String effectiveName = ItemTextBridge.effectiveNameText(previewItem);
            if (Texts.isNotBlank(effectiveName)) {
                return effectiveName;
            }
        }
        return definition.id();
    }

    private ItemStack createBaseItem(emaki.jiuwu.craft.corelib.item.ItemSource itemSource, int amount) {
        return itemSourceService == null || itemSource == null ? null : itemSourceService.createItem(itemSource, amount);
    }
}
