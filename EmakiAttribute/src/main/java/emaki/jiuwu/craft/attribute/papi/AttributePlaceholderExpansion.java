package emaki.jiuwu.craft.attribute.papi;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public final class AttributePlaceholderExpansion extends PlaceholderExpansion {

    private final EmakiAttributePlugin plugin;
    private final AttributeService attributeService;

    public AttributePlaceholderExpansion(EmakiAttributePlugin plugin, AttributeService attributeService) {
        this.plugin = plugin;
        this.attributeService = attributeService;
    }

    @Override
    public String getIdentifier() {
        return "emakiattribute";
    }

    @Override
    public String getAuthor() {
        return "Emaki";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null || Texts.isBlank(params)) {
            return "";
        }
        String normalized = params.trim().toLowerCase().replace(' ', '_');
        AttributeSnapshot snapshot = attributeService.collectCombatSnapshot(player);
        if (normalized.equals("power") || normalized.equals("combat_power")) {
            normalized = "attribute_power";
        }
        if (normalized.startsWith("resource_")) {
            return resourcePlaceholder(player, normalized.substring("resource_".length()));
        }
        Double value = attributeService.resolveAttributeValue(snapshot, normalized);
        return value == null ? "0" : Numbers.formatNumber(value, "0.##");
    }

    private String resourcePlaceholder(Player player, String params) {
        if (Texts.isBlank(params)) {
            return "";
        }
        String normalized = params.trim().toLowerCase().replace(' ', '_');
        String[] parts = normalized.split("_");
        if (parts.length == 0) {
            return "";
        }
        String resourceId = parts[0];
        String field = parts.length >= 2 ? String.join("_", java.util.Arrays.copyOfRange(parts, 1, parts.length)) : "current";
        ResourceState state = attributeService.readResourceState(player, resourceId);
        if (state == null) {
            return "0";
        }
        return switch (field) {
            case "default", "default_max" -> Numbers.formatNumber(state.defaultMax(), "0.##");
            case "bonus", "bonus_max" -> Numbers.formatNumber(state.bonusMax(), "0.##");
            case "max", "current_max" -> Numbers.formatNumber(state.currentMax(), "0.##");
            case "percent" -> Numbers.formatNumber(state.currentMax() <= 0D ? 0D : (state.currentValue() / state.currentMax()) * 100D, "0.##");
            case "current", "current_value", "value" -> Numbers.formatNumber(state.currentValue(), "0.##");
            default -> Numbers.formatNumber(state.currentValue(), "0.##");
        };
    }
}
