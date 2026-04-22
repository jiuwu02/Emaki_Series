package emaki.jiuwu.craft.gem.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.SocketOpenerConfig;

public final class GemItemMatcher {

    private static final PdcService PDC = new PdcService("emaki");
    private static final PdcPartition GEM_ITEM_PARTITION = PDC.partition("gem.item");
    private static final PdcPartition OPENER_PARTITION = PDC.partition("gem.opener");

    private final EmakiGemPlugin plugin;
    private final ItemSourceService itemSourceService;

    public GemItemMatcher(EmakiGemPlugin plugin, ItemSourceService itemSourceService) {
        this.plugin = plugin;
        this.itemSourceService = itemSourceService;
    }

    public void refresh() {
        // Reserved for future cache/index refresh logic.
    }

    public ItemSource identifyItem(ItemStack itemStack) {
        return itemSourceService == null ? null : itemSourceService.identifyItem(itemStack);
    }

    public GemDefinition matchGemItem(ItemStack itemStack) {
        GemItemInstance instance = readGemInstance(itemStack);
        return instance == null ? null : plugin.gemLoader().get(instance.gemId());
    }

    public GemItemInstance readGemInstance(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        String gemId = PDC.get(itemStack, GEM_ITEM_PARTITION, "id", org.bukkit.persistence.PersistentDataType.STRING);
        Integer level = PDC.get(itemStack, GEM_ITEM_PARTITION, "level", org.bukkit.persistence.PersistentDataType.INTEGER);
        if (Texts.isBlank(gemId)) {
            GemDefinition matched = matchGemDefinitionBySource(itemStack);
            return matched == null ? null : new GemItemInstance(matched.id(), 1, System.currentTimeMillis());
        }
        return new GemItemInstance(gemId, level == null ? 1 : level, System.currentTimeMillis());
    }

    public String readOpenerId(ItemStack itemStack) {
        return Texts.lower(PDC.get(itemStack, OPENER_PARTITION, "id", org.bukkit.persistence.PersistentDataType.STRING));
    }

    public SocketOpenerConfig matchOpenerItem(ItemStack itemStack) {
        List<SocketOpenerConfig> candidates = openerCandidates(itemStack);
        return candidates.isEmpty() ? null : candidates.getFirst();
    }

    public SocketOpenerConfig matchOpenerForType(ItemStack itemStack, String socketType) {
        if (Texts.isBlank(socketType)) {
            return matchOpenerItem(itemStack);
        }
        return openerCandidates(itemStack).stream()
                .filter(config -> config.supportsType(socketType))
                .findFirst()
                .orElse(null);
    }

    public boolean isOpenerItem(ItemStack itemStack) {
        return !openerCandidates(itemStack).isEmpty();
    }

    public boolean matchesOpenerItem(ItemStack itemStack, SocketOpenerConfig expected) {
        if (expected == null) {
            return false;
        }
        String openerId = readOpenerId(itemStack);
        if (Texts.isNotBlank(openerId)) {
            return expected.id().equals(openerId);
        }
        return openerCandidates(itemStack).stream().anyMatch(config -> expected.id().equals(config.id()));
    }

    public GemItemDefinition matchEquipment(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return null;
        }
        ItemSource identified = identifyItem(itemStack);
        for (GemItemDefinition definition : plugin.gemItemLoader().all().values()) {
            if (definition == null) {
                continue;
            }
            if (matchesItemSource(definition, identified)
                    && matchesSlotGroups(definition, itemStack.getType())
                    && matchesLore(definition, itemStack)) {
                return definition;
            }
        }
        return null;
    }

    private boolean matchesItemSource(GemItemDefinition definition, ItemSource identified) {
        if (definition.itemSources().isEmpty()) {
            return true;
        }
        if (identified == null) {
            return false;
        }
        return definition.itemSources().stream().anyMatch(source -> ItemSourceUtil.matches(source, identified));
    }

    private boolean matchesSlotGroups(GemItemDefinition definition, Material material) {
        if (definition.slotGroups().isEmpty()) {
            return true;
        }
        String type = material == null ? "" : material.name().toLowerCase(Locale.ROOT);
        for (String group : definition.slotGroups()) {
            if (matchesGroup(type, group)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesLore(GemItemDefinition definition, ItemStack itemStack) {
        if (definition.loreContains().isEmpty()) {
            return true;
        }
        List<String> plainLore = new ArrayList<>();
        List<String> lore = ItemTextBridge.loreLines(itemStack == null ? null : itemStack.getItemMeta());
        if (lore != null) {
            lore.stream().map(MiniMessages::plainText).forEach(plainLore::add);
        }
        String fullText = String.join("\n", plainLore).toLowerCase(Locale.ROOT);
        for (String token : definition.loreContains()) {
            if (!fullText.contains(Texts.lower(token))) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesGroup(String materialType, String group) {
        String normalized = Texts.lower(group);
        return switch (normalized) {
            case "weapon", "sword" -> materialType.endsWith("_sword") || materialType.endsWith("_axe");
            case "armor", "helmet", "chestplate", "leggings", "boots" -> materialType.endsWith("_helmet")
                    || materialType.endsWith("_chestplate")
                    || materialType.endsWith("_leggings")
                    || materialType.endsWith("_boots");
            case "offhand", "shield" -> materialType.endsWith("shield") || materialType.contains("totem");
            case "tool" -> materialType.endsWith("_pickaxe")
                    || materialType.endsWith("_axe")
                    || materialType.endsWith("_shovel")
                    || materialType.endsWith("_hoe");
            default -> materialType.contains(normalized);
        };
    }

    private List<SocketOpenerConfig> openerCandidates(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return List.of();
        }
        String openerId = readOpenerId(itemStack);
        if (Texts.isNotBlank(openerId)) {
            SocketOpenerConfig config = plugin.appConfig().socketOpeners().get(openerId);
            return config == null || !config.enabled() ? List.of() : List.of(config);
        }
        ItemSource identified = identifyItem(itemStack);
        if (identified == null) {
            return List.of();
        }
        return plugin.appConfig().socketOpeners().values().stream()
                .filter(config -> config != null && config.enabled())
                .filter(config -> config.itemSource() != null && ItemSourceUtil.matches(config.itemSource(), identified))
                .toList();
    }

    private GemDefinition matchGemDefinitionBySource(ItemStack itemStack) {
        ItemSource identified = identifyItem(itemStack);
        if (identified == null) {
            return null;
        }
        List<GemDefinition> matches = plugin.gemLoader().all().values().stream()
                .filter(definition -> definition != null && definition.itemSource() != null)
                .filter(definition -> ItemSourceUtil.matches(definition.itemSource(), identified))
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }
}
