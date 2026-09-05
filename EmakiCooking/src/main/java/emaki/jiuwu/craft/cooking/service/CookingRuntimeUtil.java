package emaki.jiuwu.craft.cooking.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class CookingRuntimeUtil {

    private CookingRuntimeUtil() {
    }

    static ItemStack takeOneFromMainHand(Player player) {
        if (player == null) {
            return null;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            return null;
        }
        ItemStack consumed = hand.clone();
        consumed.setAmount(1);
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
            player.getInventory().setItemInMainHand(hand);
        } else {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }
        return consumed;
    }

    static void sendActionBar(Plugin plugin, Player player, MessageService messageService, String key, Map<String, ?> replacements) {
        if (player == null) {
            return;
        }
        player.sendActionBar(MiniMessages.parse(messageService.message(key, replacements)));
    }

    static long parseLong(Object raw, long fallback) {
        return Numbers.tryParseLong(raw, fallback);
    }

    static int parseInteger(Object raw, int fallback) {
        return Numbers.tryParseInt(raw, fallback);
    }

    static ItemSourceRef parseOutputSource(Plugin plugin, Map<String, Object> output, String path) {
        if (output == null || output.isEmpty()) {
            return null;
        }
        boolean canonical = output.containsKey("item_source");
        boolean legacy = output.containsKey("item_sources");
        if (canonical && legacy) {
            warnOutputSchema(plugin, path, "item_source and item_sources cannot both be declared");
            return null;
        }
        if (output.containsKey("matcher")) {
            warnOutputSchema(plugin, path, "matcher is not allowed on output nodes");
            return null;
        }
        if (canonical) {
            Object raw = output.get("item_source");
            if (raw instanceof Collection<?> || raw instanceof Iterable<?>) {
                warnOutputSchema(plugin, path + ".item_source", "canonical item_source must be a single source");
                return null;
            }
            ItemSourceRef source = ItemSourceUtil.parse(raw);
            if (source == null) {
                warnOutputSchema(plugin, path + ".item_source", "item_source is invalid");
            }
            return source;
        }
        if (!legacy) {
            warnOutputSchema(plugin, path, "missing item_source");
            return null;
        }
        List<Object> values = ConfigNodes.asObjectList(output.get("item_sources"));
        if (values.size() != 1) {
            warnOutputSchema(plugin, path + ".item_sources", "legacy item_sources must contain exactly one source");
            return null;
        }
        ItemSourceRef source = ItemSourceUtil.parse(values.getFirst());
        if (source == null) {
            warnOutputSchema(plugin, path + ".item_sources[0]", "legacy item_sources entry is invalid");
            return null;
        }
        warnOutputSchema(plugin, path + ".item_sources", "legacy item_sources is accepted; migrate to item_source");
        return source;
    }

    private static void warnOutputSchema(Plugin plugin, String path, String message) {
        if (plugin != null) {
            plugin.getLogger().warning("[OutputSchema] " + path + ": " + message);
        }
    }

    static Map<String, Object> buildStateRoot(StationType stationType, StationCoordinates coordinates) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("station_type", stationType.folderName());
        root.put("world", coordinates.world());
        root.put("x", coordinates.x());
        root.put("y", coordinates.y());
        root.put("z", coordinates.z());
        return root;
    }

    static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (Exception _) {
            return null;
        }
    }

    static String resolveBlockId(EmakiCookingPlugin plugin, Block block) {
        if (block == null) {
            return "";
        }
        if (plugin != null) {
            String custom = identifyCustomBlock(plugin.craftEngineBlockBridge(), block);
            if (Texts.isBlank(custom)) {
                custom = identifyCustomBlock(plugin.itemsAdderBlockBridge(), block);
            }
            if (Texts.isBlank(custom)) {
                custom = identifyCustomBlock(plugin.nexoBlockBridge(), block);
            }
            if (Texts.isBlank(custom)) {
                custom = identifyCustomBlock(plugin.oraxenBlockBridge(), block);
            }
            if (Texts.isNotBlank(custom)) {
                return custom;
            }
        }
        Material material = block.getType();
        return material == null ? "" : material.getKey().toString();
    }

    private static String identifyCustomBlock(CustomBlockBridge bridge, Block block) {
        if (bridge == null || !bridge.available() || !bridge.isCustomBlock(block)) {
            return "";
        }
        String identifier = bridge.identifyBlock(block);
        return Texts.isBlank(identifier) ? "" : identifier;
    }
}
