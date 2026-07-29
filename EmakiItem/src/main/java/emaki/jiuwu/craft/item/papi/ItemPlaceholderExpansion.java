package emaki.jiuwu.craft.item.papi;

import java.util.Locale;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.placeholder.AbstractEmakiPlaceholderExpansion;
import emaki.jiuwu.craft.item.EmakiItemPlugin;

public final class ItemPlaceholderExpansion extends AbstractEmakiPlaceholderExpansion {

    private final EmakiItemPlugin plugin;

    public ItemPlaceholderExpansion(EmakiItemPlugin plugin) {
        super(plugin);
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
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        String normalized = params.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "held_id" -> plugin.identifier().identify(held);
            case "held_name" -> resolveHeldName(held);
            case "held_components" -> plugin.componentInspector().idList(held);
            case "held_components_raw" -> plugin.componentInspector().raw(held);
            case "loaded_count" -> Integer.toString(plugin.itemLoader().all().size());
            default -> resolveComponentPlaceholder(held, normalized);
        };
    }

    private String resolveComponentPlaceholder(ItemStack held, String params) {
        if (params.startsWith("held_component_has_")) {
            return plugin.componentInspector().contains(held, params.substring("held_component_has_".length())) ? "1" : "0";
        }
        if (params.startsWith("held_component_value_")) {
            return plugin.componentInspector().value(held, params.substring("held_component_value_".length()));
        }
        if (params.startsWith("held_component_json_")) {
            return plugin.componentInspector().prettyJson(held, params.substring("held_component_json_".length()));
        }
        return "";
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
