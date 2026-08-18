package emaki.jiuwu.craft.gem.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;

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
        int normalizedLevel = Math.max(1, level);
        itemStack = applyGemPresentation(itemStack, definition, normalizedLevel);
        writeInstanceFields(itemStack, new GemItemInstance(definition.id(), normalizedLevel, System.currentTimeMillis()));
        return itemStack;
    }

    /**
     * 就地把整份实例数据写回宝石物品，并按新等级刷新展示。
     *
     * <p>与 {@link #createGemItem} 的区别是不新建物品、不改数量，因此适合强化/升级流程在既有
     * 物品上推进等级或阶段。{@code instanceId} / {@code affixes} / {@code matrices} /
     * {@code extensions} / {@code dataVersion} 一并落盘，不会因为一次升级而丢失。
     *
     * @param itemStack 待写回的宝石物品；{@code null} 或空气直接忽略
     * @param instance  目标实例数据；{@code null} 直接忽略
     * @return 是否实际写入
     */
    public boolean applyInstance(ItemStack itemStack, GemItemInstance instance) {
        if (itemStack == null || itemStack.getType().isAir() || instance == null) {
            return false;
        }
        GemDefinition definition = plugin == null || plugin.gemLoader() == null
                ? null
                : plugin.gemLoader().get(instance.gemId());
        if (definition == null) {
            return false;
        }
        ItemStack presented = applyGemPresentation(itemStack, definition, instance.level());
        if (presented != null && presented != itemStack && presented.hasItemMeta()) {
            itemStack.setItemMeta(presented.getItemMeta());
        }
        writeInstanceFields(itemStack, instance);
        return true;
    }

    /**
     * 写入宝石实例的兼容标量字段与完整集合快照。
     *
     * <p>标量字段保留给旧物品和外部 Matcher 兼容；{@code instance_data} 才是独立宝石实例的完整
     * 持久化载荷，覆盖实例身份、阶段、词条、矩阵、扩展和数据版本。
     */
    private void writeInstanceFields(ItemStack itemStack, GemItemInstance instance) {
        PDC.set(itemStack, GEM_ITEM_PARTITION, "id", PersistentDataType.STRING, instance.gemId());
        PDC.set(itemStack, GEM_ITEM_PARTITION, "level", PersistentDataType.INTEGER, instance.level());
        PDC.set(itemStack, GEM_ITEM_PARTITION, "updated_at", PersistentDataType.LONG, instance.updatedAt());
        PDC.set(itemStack, GEM_ITEM_PARTITION, "instance_id", PersistentDataType.STRING, instance.instanceId());
        PDC.set(itemStack, GEM_ITEM_PARTITION, "stage", PersistentDataType.INTEGER, instance.stage());
        PDC.set(itemStack, GEM_ITEM_PARTITION, "data_version", PersistentDataType.INTEGER, instance.dataVersion());
        PDC.writeBlob(itemStack, GEM_ITEM_PARTITION, "instance_data", GemItemInstance.CODEC, instance);
    }

    public ItemStack recreateGemItem(GemItemInstance instance, int amount) {
        if (instance == null || plugin == null || plugin.gemLoader() == null) {
            return null;
        }
        GemDefinition definition = plugin.gemLoader().get(instance.gemId());
        ItemStack recreated = createGemItem(definition, instance.level(), amount);
        if (recreated == null) {
            return null;
        }
        writeInstanceFields(recreated, instance);
        return recreated;
    }

    public Map<String, Object> gemPlaceholders(GemDefinition definition, int level, Integer oldLevel) {
        int normalizedLevel = Math.max(1, level);
        int normalizedOldLevel = oldLevel == null ? normalizedLevel : Math.max(1, oldLevel);
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("gem_id", definition == null ? "" : definition.id());
        placeholders.put("display_name", resolveGemDisplayName(definition, normalizedLevel));
        placeholders.put("old_display_name", resolveGemDisplayName(definition, normalizedOldLevel));
        placeholders.put("current_level", normalizedLevel);
        placeholders.put("target_level", normalizedLevel);
        placeholders.put("level", normalizedLevel);
        placeholders.put("gem_type", definition == null ? "universal" : definition.gemType());
        if (definition != null) {
            definition.statsForLevel(normalizedLevel).forEach((key, value) -> placeholders.put(key, Numbers.formatNumber(value, plugin.appConfig().numberFormat())));
        }
        return placeholders;
    }

    private ItemStack applyGemPresentation(ItemStack itemStack, GemDefinition definition, int level) {
        if (itemStack == null || definition == null) {
            return itemStack;
        }
        Map<String, Object> placeholders = gemPlaceholders(definition, level, null);
        Map<String, ItemComponentPatch> patches = new LinkedHashMap<>();
        String displayName = resolveGemDisplayName(definition, level);
        if (Texts.isNotBlank(displayName)) {
            patches.put("minecraft:custom_name", ItemComponentPatch.set(displayName));
        }
        if (!definition.lore().isEmpty()) {
            patches.put("minecraft:lore", ItemComponentPatch.set(List.copyOf(definition.lore())));
        }
        if (definition.customModelData() != null) {
            patches.put("minecraft:custom_model_data", ItemComponentPatch.set(Map.of(
                    "floats", List.of(definition.customModelData().floatValue())
            )));
        }
        return GuiItemBuilder.apply(itemStack, new ConfiguredItemDefinition(null, patches), placeholders,
                plugin.coreLib().configuredItemService());
    }

    public String resolveGemDisplayName(GemDefinition definition, int level) {
        if (definition == null) {
            return "";
        }
        String configuredDisplayName = "";
        GemDefinition.GemStage stage = definition.stage(level);
        if (stage != null && Texts.isNotBlank(stage.displayName())) {
            configuredDisplayName = stage.displayName();
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

    private ItemStack createBaseItem(ItemSourceRef itemSource, int amount) {
        return itemSourceService == null || itemSource == null ? null : itemSourceService.createItem(itemSource, amount);
    }
}
