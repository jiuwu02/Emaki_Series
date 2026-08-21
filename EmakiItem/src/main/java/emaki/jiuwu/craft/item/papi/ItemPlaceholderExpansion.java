package emaki.jiuwu.craft.item.papi;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.placeholder.AbstractEmakiPlaceholderExpansion;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.api.ItemStateKey;

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
            case "held_state_keys" -> resolveReservedStatePlaceholder(held, normalized, resolveStateKeys(held));
            case "held_state_revision" -> resolveReservedStatePlaceholder(held, normalized,
                    Long.toString(plugin.stateService().snapshot(held).metadata().revision()));
            case "held_state_instance" -> resolveReservedStatePlaceholder(held, normalized,
                    plugin.stateService().snapshot(held).metadata().instanceId());
            case "held_state_schema" -> resolveReservedStatePlaceholder(held, normalized,
                    Integer.toString(plugin.stateService().snapshot(held).metadata().schemaVersion()));
            case "held_state_valid" -> resolveReservedStatePlaceholder(held, normalized,
                    plugin.stateService().snapshot(held).metadata().valid() ? "1" : "0");
            default -> resolveStatePlaceholder(held, normalized);
        };
    }

    private String resolveReservedStatePlaceholder(ItemStack held, String params, String metadataValue) {
        Object shadowed = findStateValue(held, params.substring("held_state_".length()));
        return shadowed == null ? metadataValue : String.valueOf(shadowed);
    }

    private String resolveStatePlaceholder(ItemStack held, String params) {
        if (params.startsWith("held_state_has_")) {
            return findStateValue(held, params.substring("held_state_has_".length())) == null ? "0" : "1";
        }
        if (params.startsWith("held_state_")) {
            Object value = findStateValue(held, params.substring("held_state_".length()));
            return value == null ? "" : String.valueOf(value);
        }
        return resolveComponentPlaceholder(held, params);
    }

    private Object findStateValue(ItemStack held, String key) {
        String normalizedKey = Texts.toStringSafe(key).trim().toLowerCase(Locale.ROOT);
        if (normalizedKey.isBlank() || held == null || held.getType().isAir()) {
            return null;
        }
        for (Map.Entry<ItemStateKey<?>, Object> entry : plugin.stateService().snapshot(held).values().entrySet()) {
            if (entry.getKey().key().equals(normalizedKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String resolveStateKeys(ItemStack held) {
        if (held == null || held.getType().isAir()) {
            return "";
        }
        return plugin.stateService().snapshot(held).values().keySet().stream()
                .map(ItemStateKey::key)
                .sorted()
                .collect(Collectors.joining(","));
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
