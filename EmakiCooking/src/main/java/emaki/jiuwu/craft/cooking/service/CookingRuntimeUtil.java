package emaki.jiuwu.craft.cooking.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import org.bukkit.Material;
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
        AdventureSupport.sendActionBar(plugin, player, messageService.render(messageService.message(key, replacements)));
    }

    static long parseLong(Object raw, long fallback) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw == null) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    static int parseInteger(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    static Map<String, Object> buildStateRoot(StationType stationType, StationCoordinates coordinates) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema_version", "2.0.0");
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
        } catch (Exception ignored) {
            return null;
        }
    }
}
