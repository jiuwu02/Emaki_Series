package emaki.jiuwu.craft.item.papi;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public final class ItemPlaceholderExpansion extends PlaceholderExpansion {

    private final EmakiItemPlugin plugin;

    public ItemPlaceholderExpansion(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "emakiitem";
    }

    @Override
    public @NotNull String getAuthor() {
        return "JiuWu";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        return switch (params.toLowerCase()) {
            case "held_id" -> plugin.identifier().identify(player.getInventory().getItemInMainHand());
            case "held_name" -> resolveHeldName(player.getInventory().getItemInMainHand());
            case "loaded_count" -> Integer.toString(plugin.itemLoader().all().size());
            default -> "";
        };
    }

    private String resolveHeldName(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "";
        }
        try {
            return ItemTextBridge.effectiveNamePlain(item);
        } catch (Exception ignored) {
            return "";
        }
    }
}
