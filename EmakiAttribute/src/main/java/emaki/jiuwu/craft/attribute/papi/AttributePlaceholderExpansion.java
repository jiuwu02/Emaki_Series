package emaki.jiuwu.craft.attribute.papi;

import java.util.Arrays;
import java.util.Locale;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

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
        String normalized = normalizeToken(params);
        AttributeSnapshot snapshot = attributeService.collectCombatSnapshot(player);
        if (normalized.startsWith("resource_")) {
            return resourcePlaceholder(player, normalized.substring("resource_".length()));
        }
        Double value = attributeService.resolveAttributeValue(snapshot, canonicalAttributePlaceholder(normalized));
        return value == null ? "0" : Numbers.formatNumber(value, "0.##");
    }

    private String resourcePlaceholder(Player player, String params) {
        ResourceQuery query = parseResourceQuery(params);
        if (Texts.isBlank(query.resourceId())) {
            return "";
        }
        ResourceState state = attributeService.readResourceState(player, query.resourceId());
        if (state == null) {
            return "0";
        }
        return switch (query.field()) {
            case "default" ->
                Numbers.formatNumber(state.defaultMax(), "0.##");
            case "bonus" ->
                Numbers.formatNumber(state.bonusMax(), "0.##");
            case "max" ->
                Numbers.formatNumber(state.currentMax(), "0.##");
            case "percent" ->
                Numbers.formatNumber(state.currentMax() <= 0D ? 0D : (state.currentValue() / state.currentMax()) * 100D, "0.##");
            case "current" ->
                Numbers.formatNumber(state.currentValue(), "0.##");
            default ->
                "0";
        };
    }

    static String canonicalAttributePlaceholder(String params) {
        String normalized = normalizeToken(params);
        return "power".equals(normalized) ? "attribute_power" : normalized;
    }

    static String resourceId(String params) {
        return parseResourceQuery(params).resourceId();
    }

    static String resourceField(String params) {
        return parseResourceQuery(params).field();
    }

    private static ResourceQuery parseResourceQuery(String params) {
        String normalized = normalizeToken(params);
        if (Texts.isBlank(normalized)) {
            return new ResourceQuery("", "");
        }
        String[] parts = normalized.split("_");
        if (parts.length == 0) {
            return new ResourceQuery("", "");
        }
        String resourceId = parts[0];
        String field = parts.length >= 2 ? String.join("_", Arrays.copyOfRange(parts, 1, parts.length)) : "current";
        return new ResourceQuery(resourceId, canonicalResourceField(field));
    }

    private static String canonicalResourceField(String field) {
        String normalized = normalizeToken(field);
        return switch (normalized) {
            case "current", "max", "default", "bonus", "percent" ->
                normalized;
            default ->
                "";
        };
    }

    private static String normalizeToken(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private record ResourceQuery(String resourceId, String field) {

    }
}
