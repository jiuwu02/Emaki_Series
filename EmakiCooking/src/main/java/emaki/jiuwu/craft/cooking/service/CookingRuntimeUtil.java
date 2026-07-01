package emaki.jiuwu.craft.cooking.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.text.Texts;
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
        AdventureSupport.sendActionBar(plugin, player, messageService.message(key, replacements));
    }

    static long parseLong(Object raw, long fallback) {
        return Numbers.tryParseLong(raw, fallback);
    }

    static int parseInteger(Object raw, int fallback) {
        return Numbers.tryParseInt(raw, fallback);
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

    /**
     * 解析方块 id：优先识别自定义方块（CraftEngine / ItemsAdder / Nexo / Oraxen），
     * 命中则返回各桥接层给出的方块标识，否则回退到原版 Material 名（小写）。
     *
     * @param plugin 主插件实例（提供 4 个自定义方块桥接）
     * @param block  目标方块，可为 null
     * @return 方块 id；block 为 null 时返回空串
     */
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
