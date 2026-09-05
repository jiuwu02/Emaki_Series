package emaki.jiuwu.craft.gem.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.matcher.ItemRequirement;
import emaki.jiuwu.craft.corelib.matcher.MatchContext;
import emaki.jiuwu.craft.corelib.matcher.Matcher;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.SocketOpenerConfig;

public final class GemItemMatcher {

    private static final PdcService PDC = new PdcService("emaki");

    private static final PdcPartition GEM_ITEM_PARTITION = PDC.partition("gem_item");
    private static final PdcPartition OPENER_PARTITION = PDC.partition("gem_opener");

    private static final String LEGACY_GEM_ITEM_PATH = "gem.item";
    private static final String LEGACY_GEM_OPENER_PATH = "gem.opener";

    private final EmakiGemPlugin plugin;
    private final ItemSourceService itemSourceService;

    public GemItemMatcher(EmakiGemPlugin plugin, ItemSourceService itemSourceService) {
        this.plugin = plugin;
        this.itemSourceService = itemSourceService;
    }

    public void refresh() {
    }

    public ItemSourceRef identifyItem(ItemStack itemStack) {
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
        GemItemInstance stored = readStoredGemInstance(itemStack);
        if (stored != null) {
            return stored;
        }
        GemDefinition matched = matchGemDefinitionBySource(itemStack);
        return matched == null ? null : new GemItemInstance(matched.id(), 1, System.currentTimeMillis());
    }

    public GemItemInstance readStoredGemInstance(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        GemItemInstance snapshot = PDC.readBlobMigrating(
                itemStack, GEM_ITEM_PARTITION, LEGACY_GEM_ITEM_PATH, "instance_data", GemItemInstance.CODEC);
        if (snapshot != null && Texts.isNotBlank(snapshot.gemId())) {
            return snapshot;
        }
        String gemId = PDC.getMigrating(
                itemStack, GEM_ITEM_PARTITION, LEGACY_GEM_ITEM_PATH, "id", PersistentDataType.STRING);
        if (Texts.isBlank(gemId)) {
            return null;
        }
        Integer level = PDC.getMigrating(
                itemStack, GEM_ITEM_PARTITION, LEGACY_GEM_ITEM_PATH, "level", PersistentDataType.INTEGER);
        Long updatedAt = PDC.getMigrating(
                itemStack, GEM_ITEM_PARTITION, LEGACY_GEM_ITEM_PATH, "updated_at", PersistentDataType.LONG);
        String instanceId = PDC.getMigrating(
                itemStack, GEM_ITEM_PARTITION, LEGACY_GEM_ITEM_PATH, "instance_id", PersistentDataType.STRING);
        Integer stage = PDC.getMigrating(
                itemStack, GEM_ITEM_PARTITION, LEGACY_GEM_ITEM_PATH, "stage", PersistentDataType.INTEGER);
        Integer dataVersion = PDC.getMigrating(
                itemStack, GEM_ITEM_PARTITION, LEGACY_GEM_ITEM_PATH, "data_version", PersistentDataType.INTEGER);
        return new GemItemInstance(
                gemId,
                level == null ? 1 : level,
                updatedAt == null ? System.currentTimeMillis() : updatedAt,
                instanceId,
                stage == null ? 0 : stage,
                List.of(),
                Map.of(),
                Map.of(),
                dataVersion == null ? GemItemInstance.CURRENT_DATA_VERSION : dataVersion
        );
    }

    public String readOpenerId(ItemStack itemStack) {
        return Texts.lower(PDC.getMigrating(
                itemStack, OPENER_PARTITION, LEGACY_GEM_OPENER_PATH, "id", PersistentDataType.STRING));
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
        ItemSourceRef identified = identifyItem(itemStack);
        for (GemItemDefinition definition : plugin.gemItemLoader().all().values()) {
            if (definition == null) {
                continue;
            }
            if (matchesSlotGroups(definition, itemStack.getType())
                    && satisfiesRequirement(definition.recognition(), itemStack, identified)) {
                return definition;
            }
        }
        return null;
    }

    private boolean satisfiesMatcher(Matcher matcher, ItemStack itemStack, ItemSourceRef identified) {
        if (matcher == null) {
            return true;
        }
        try {
            return matcher.test(MatchContext.of(itemStack, identified, null));
        } catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().warning("Gem matcher evaluation failed, treating the item as unmatched: "
                    + failure.getMessage());
            return false;
        }
    }

    private boolean satisfiesRequirement(ItemRequirement requirement, ItemStack itemStack, ItemSourceRef identified) {
        if (requirement == null || requirement.empty()) {
            return false;
        }
        try {
            return requirement.test(itemStack, identified, null);
        } catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().warning("Gem recognition evaluation failed, treating the item as unmatched: "
                    + failure.getMessage());
            return false;
        }
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
        ItemSourceRef identified = identifyItem(itemStack);
        return plugin.appConfig().socketOpeners().values().stream()
                .filter(config -> config != null && config.enabled())
                .filter(config -> acceptsOpenerItem(config, itemStack, identified))
                .toList();
    }

    private boolean acceptsOpenerItem(SocketOpenerConfig config, ItemStack itemStack, ItemSourceRef identified) {
        return satisfiesRequirement(config.recognition(), itemStack, identified);
    }

    private GemDefinition matchGemDefinitionBySource(ItemStack itemStack) {
        ItemSourceRef identified = identifyItem(itemStack);
        List<GemDefinition> matches = plugin.gemLoader().all().values().stream()
                .filter(definition -> definition != null)
                .filter(definition -> acceptsGemItem(definition, itemStack, identified))
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private boolean acceptsGemItem(GemDefinition definition, ItemStack itemStack, ItemSourceRef identified) {
        return satisfiesRequirement(definition.recognition(), itemStack, identified);
    }
}
