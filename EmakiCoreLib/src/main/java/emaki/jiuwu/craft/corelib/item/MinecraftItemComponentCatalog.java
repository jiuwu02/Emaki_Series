package emaki.jiuwu.craft.corelib.item;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;


public final class MinecraftItemComponentCatalog {

    public record Entry(String componentId,
            String minimumMinecraftVersion,
            String valueFormat,
            boolean nonValued) {
    }

    private final Map<String, Entry> entries;
    private final MinecraftVersion serverVersion;

    public MinecraftItemComponentCatalog() {
        this.entries = createEntries();
        this.serverVersion = MinecraftVersion.parse(detectMinecraftVersion());
    }

    public Map<String, Entry> entries() {
        return entries;
    }

    public Entry entry(String componentId) {
        return entries.get(componentId);
    }

    public String serverVersion() {
        return serverVersion.text();
    }

    public boolean isKnownFutureComponent(String componentId) {
        Entry entry = entries.get(componentId);
        return entry != null && serverVersion.compareTo(MinecraftVersion.parse(entry.minimumMinecraftVersion())) < 0;
    }

    private Map<String, Entry> createEntries() {
        Map<String, Entry> resourceEntries = loadResourceEntries();
        if (!resourceEntries.isEmpty()) {
            return resourceEntries;
        }
        Map<String, Entry> result = new LinkedHashMap<>();
        add(result, "max_stack_size", "1.20.5", "integer 1..99");
        add(result, "max_damage", "1.20.5", "positive integer");
        add(result, "damage", "1.20.5", "non-negative integer");
        addUnit(result, "unbreakable", "1.20.5");
        add(result, "custom_name", "1.20.5", "MiniMessage string or vanilla text component map/list");
        add(result, "item_name", "1.20.5", "MiniMessage string or vanilla text component map/list");
        add(result, "lore", "1.20.5", "MiniMessage string/list or vanilla text component list");
        add(result, "rarity", "1.20.5", "common, uncommon, rare, or epic");
        add(result, "enchantments", "1.20.5", "direct enchantment resource id to level map (Minecraft 1.21.5+)");
        add(result, "can_place_on", "1.20.5", "vanilla adventure predicate map");
        add(result, "can_break", "1.20.5", "vanilla adventure predicate map");
        add(result, "attribute_modifiers", "1.20.5", "direct attribute modifier list (Minecraft 1.21.5+)");
        add(result, "custom_model_data", "1.20.5", "map containing floats/flags/strings/colors");
        add(result, "repair_cost", "1.20.5", "non-negative integer");
        add(result, "enchantment_glint_override", "1.20.5", "boolean");
        addUnit(result, "intangible_projectile", "1.20.5");
        add(result, "food", "1.20.5", "vanilla food properties map");
        add(result, "consumable", "1.21.2", "vanilla consumable map");
        add(result, "use_remainder", "1.21.2", "item stack map");
        add(result, "use_cooldown", "1.21.2", "vanilla cooldown map");
        add(result, "damage_resistant", "1.21.2", "damage type tag map");
        add(result, "tool", "1.20.5", "vanilla tool rules map");
        add(result, "weapon", "1.21.5", "vanilla weapon properties map");
        add(result, "enchantable", "1.21.2", "map containing positive integer value");
        add(result, "equippable", "1.21.2", "vanilla equippable map");
        add(result, "repairable", "1.21.2", "repair item/tag map");
        addUnit(result, "glider", "1.21.2");
        add(result, "item_model", "1.21.2", "namespaced resource id");
        add(result, "tooltip_style", "1.21.2", "namespaced resource id");
        add(result, "tooltip_display", "1.21.5", "map containing hide_tooltip/hidden_components");
        add(result, "death_protection", "1.21.2", "vanilla death protection map");
        add(result, "blocks_attacks", "1.21.5", "vanilla blocking properties map");
        add(result, "stored_enchantments", "1.20.5", "vanilla enchantment component map");
        add(result, "dyed_color", "1.20.5", "RGB integer or color map");
        add(result, "potion_contents", "1.20.5", "vanilla potion contents map");
        add(result, "charged_projectiles", "1.20.5", "item stack list");
        add(result, "bundle_contents", "1.20.5", "item stack list");
        add(result, "trim", "1.20.5", "trim material/pattern map");
        add(result, "custom_data", "1.20.5", "plain map or {$snbt: raw SNBT}");
        add(result, "entity_data", "1.20.5", "plain map or {$snbt: raw SNBT}");
        add(result, "block_entity_data", "1.20.5", "plain map or {$snbt: raw SNBT}");
        add(result, "block_state", "1.20.5", "block state property map");

        add(result, "use_effects", "1.21.11", "vanilla use effects map");
        add(result, "minimum_attack_charge", "1.21.11", "floating-point number");
        add(result, "damage_type", "1.21.11", "damage type resource id");
        add(result, "piercing_weapon", "1.21.11", "vanilla piercing weapon map");
        add(result, "kinetic_weapon", "1.21.11", "vanilla kinetic weapon map");
        add(result, "attack_range", "1.21.11", "vanilla attack range map");
        add(result, "swing_animation", "1.21.11", "vanilla swing animation map");
        add(result, "break_sound", "1.21.5", "sound resource id");
        return Collections.unmodifiableMap(result);
    }

    private Map<String, Entry> loadResourceEntries() {
        try (InputStream inputStream = MinecraftItemComponentCatalog.class.getResourceAsStream("/item-components.yml")) {
            if (inputStream == null) {
                return Map.of();
            }
            YamlSection root = YamlFiles.load(inputStream);
            List<?> configuredEntries = root.getList("components");
            Map<String, Entry> result = new LinkedHashMap<>();
            for (Object raw : configuredEntries) {
                String componentId = ConfigNodes.string(raw, "id", null);
                String minimumVersion = ConfigNodes.string(raw, "since", null);
                if (Texts.isBlank(componentId) || Texts.isBlank(minimumVersion)) {
                    continue;
                }
                String normalizedId = componentId.contains(":")
                        ? Texts.lower(componentId).trim()
                        : "minecraft:" + Texts.lower(componentId).trim();
                if (result.containsKey(normalizedId)) {
                    throw new IllegalArgumentException("Duplicate item component catalog id: " + normalizedId);
                }
                result.put(normalizedId, new Entry(
                        normalizedId,
                        minimumVersion.trim(),
                        ConfigNodes.string(raw, "format", "vanilla component value"),
                        ConfigNodes.bool(raw, "non_valued", false)
                ));
            }
            return Collections.unmodifiableMap(result);
        } catch (IOException | RuntimeException ignored) {
            return Map.of();
        }
    }

    private void add(Map<String, Entry> entries, String id, String minimumVersion, String format) {
        String namespacedId = "minecraft:" + id;
        entries.put(namespacedId, new Entry(namespacedId, minimumVersion, format, false));
    }

    private void addUnit(Map<String, Entry> entries, String id, String minimumVersion) {
        String namespacedId = "minecraft:" + id;
        entries.put(namespacedId, new Entry(namespacedId, minimumVersion, "unit: true, null, or empty map", true));
    }

    private String detectMinecraftVersion() {
        try {
            Object value = Bukkit.class.getMethod("getMinecraftVersion").invoke(null);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        try {
            String bukkitVersion = Bukkit.getBukkitVersion();
            int separator = bukkitVersion.indexOf('-');
            return separator < 0 ? bukkitVersion : bukkitVersion.substring(0, separator);
        } catch (RuntimeException ignored) {
            return "1.21.8";
        }
    }

    private record MinecraftVersion(int major, int minor, int patch, String text) implements Comparable<MinecraftVersion> {

        private static MinecraftVersion parse(String raw) {
            String normalized = raw == null ? "" : raw.trim();
            String[] parts = normalized.split("[^0-9]+");
            int[] numbers = new int[]{0, 0, 0};
            int target = 0;
            for (String part : parts) {
                if (part.isBlank() || target >= numbers.length) {
                    continue;
                }
                try {
                    numbers[target++] = Integer.parseInt(part);
                } catch (NumberFormatException ignored) {
                }
            }
            String text = numbers[0] + "." + numbers[1] + "." + numbers[2];
            return new MinecraftVersion(numbers[0], numbers[1], numbers[2], text);
        }

        @Override
        public int compareTo(MinecraftVersion other) {
            int compared = Integer.compare(major, other.major);
            if (compared != 0) {
                return compared;
            }
            compared = Integer.compare(minor, other.minor);
            return compared != 0 ? compared : Integer.compare(patch, other.patch);
        }
    }
}
