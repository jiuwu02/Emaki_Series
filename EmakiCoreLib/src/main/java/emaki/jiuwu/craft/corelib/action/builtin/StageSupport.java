package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.corelib.action.pipeline.compile.ValueParsers;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class StageSupport {

    private StageSupport() {
    }

    public static Entity entity(CoreActionSubject subject) {
        return subject == null ? null : subject.entityOrNull();
    }

    public static LivingEntity livingEntity(CoreActionSubject subject) {
        Entity entity = entity(subject);
        return entity instanceof LivingEntity living ? living : null;
    }

    public static Player player(CoreActionSubject subject) {
        Entity entity = entity(subject);
        return entity instanceof Player playerEntity ? playerEntity : null;
    }

    public static World world(String requestedWorld, World fallback) {
        String candidate = Texts.trim(requestedWorld);
        if (candidate.isEmpty()) {
            return fallback;
        }
        World direct = Bukkit.getWorld(candidate);
        if (direct != null) {
            return direct;
        }
        for (World world : Bukkit.getWorlds()) {
            if (worldMatches(world, candidate)) {
                return world;
            }
        }
        return null;
    }

    private static boolean worldMatches(World world, String candidate) {
        if (world == null || Texts.isBlank(candidate)) {
            return false;
        }
        if (candidate.equalsIgnoreCase(world.getName())) {
            return true;
        }
        NamespacedKey key = world.getKey();
        if (key == null) {
            return false;
        }
        if (candidate.equalsIgnoreCase(key.toString()) || candidate.equalsIgnoreCase(key.getKey())) {
            return true;
        }
        int separator = candidate.indexOf(':');
        if (separator <= 0 || separator + 1 >= candidate.length()) {
            return false;
        }
        String keyPath = candidate.substring(separator + 1);
        return keyPath.equalsIgnoreCase(world.getName()) || keyPath.equalsIgnoreCase(key.getKey());
    }

    public static ItemSourceRef itemSource(String raw) {
        return ItemSourceUtil.parse(Texts.trim(raw));
    }

    public static String shorthand(ItemSourceRef source) {
        return Texts.toStringSafe(ItemSourceUtil.toShorthand(source));
    }

    public static Slot slot(String raw, String fallback) {
        String value = Texts.isBlank(raw) ? fallback : raw;
        if (Texts.isBlank(value)) {
            return null;
        }
        String normalized = Texts.trim(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "mainhand", "main_hand", "hand" -> new Slot("mainhand",
                    PlayerInventory::getItemInMainHand, PlayerInventory::setItemInMainHand);
            case "offhand", "off_hand" -> new Slot("offhand",
                    PlayerInventory::getItemInOffHand, PlayerInventory::setItemInOffHand);
            case "helmet" -> new Slot("helmet", PlayerInventory::getHelmet, PlayerInventory::setHelmet);
            case "chestplate", "chest" -> new Slot("chestplate",
                    PlayerInventory::getChestplate, PlayerInventory::setChestplate);
            case "leggings", "legs" -> new Slot("leggings",
                    PlayerInventory::getLeggings, PlayerInventory::setLeggings);
            case "boots" -> new Slot("boots", PlayerInventory::getBoots, PlayerInventory::setBoots);
            default -> indexedSlot(normalized);
        };
    }

    private static Slot indexedSlot(String raw) {
        String numeric = raw.startsWith("slot_") ? raw.substring("slot_".length()) : raw;
        if (raw.startsWith("hotbar_")) {
            numeric = raw.substring("hotbar_".length());
        }
        Integer index = ValueParsers.parseIntNullable(numeric);
        if (index == null || index < 0 || index > 35) {
            return null;
        }
        int resolved = index;
        return new Slot("slot_" + resolved,
                inventory -> inventory.getItem(resolved),
                (inventory, itemStack) -> inventory.setItem(resolved, itemStack));
    }

    public static boolean isEmpty(ItemStack itemStack) {
        return itemStack == null || itemStack.getType().isAir();
    }

    public static Material material(String raw) {
        if (Texts.isBlank(raw)) {
            return null;
        }
        Material resolved = ItemSourceUtil.resolveVanillaMaterial(raw);
        if (resolved != null) {
            return resolved;
        }
        String trimmed = Texts.trim(raw);
        resolved = Material.matchMaterial(trimmed);
        if (resolved != null) {
            return resolved;
        }
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
            return Material.matchMaterial(trimmed.substring("minecraft:".length()));
        }
        return null;
    }

    public record Slot(String id, Getter getter, Setter setter) {

        public ItemStack get(PlayerInventory inventory) {
            return inventory == null ? null : getter.get(inventory);
        }

        public void set(PlayerInventory inventory, ItemStack itemStack) {
            if (inventory != null) {
                setter.set(inventory, isEmpty(itemStack) ? null : itemStack);
            }
        }

        public void clear(PlayerInventory inventory) {
            set(inventory, null);
        }
    }

    @FunctionalInterface
    public interface Getter {

        ItemStack get(PlayerInventory inventory);
    }

    @FunctionalInterface
    public interface Setter {

        void set(PlayerInventory inventory, ItemStack itemStack);
    }
}
