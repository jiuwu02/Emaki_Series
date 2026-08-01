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
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Shared parsing and subject helpers for the builtin pipeline stages.
 *
 * <p>Deliberately does not reuse the v1 {@code WorldArgumentResolver} / {@code ActionInventorySlot} /
 * {@code ActionItemSourceArguments} helpers even though the rules are the same. Those classes are
 * package-private inside {@code action.builtin}, and phase 6 deletes that package outright; a
 * cross-package reference would make the v2 stages depend on code that is scheduled for removal.</p>
 */
public final class StageSupport {

    private StageSupport() {
    }

    /** {@return the entity carried by {@code subject}, or {@code null} when it carries none} */
    public static Entity entity(CoreActionSubject subject) {
        return subject == null ? null : subject.entityOrNull();
    }

    /** {@return the subject's entity when it is a living entity, otherwise {@code null}} */
    public static LivingEntity livingEntity(CoreActionSubject subject) {
        Entity entity = entity(subject);
        return entity instanceof LivingEntity living ? living : null;
    }

    /** {@return the subject's entity when it is a player, otherwise {@code null}} */
    public static Player player(CoreActionSubject subject) {
        Entity entity = entity(subject);
        return entity instanceof Player playerEntity ? playerEntity : null;
    }

    /**
     * Resolves a world argument using the same three-step match as v1.
     *
     * <p>Name, then {@code namespace:key}, then the key path after the colon. Reimplemented here for
     * the reason given in the class Javadoc.</p>
     *
     * @param requestedWorld raw argument, may be blank
     * @param fallback world used when the argument is blank
     * @return the resolved world, or {@code null} when the argument names no known world
     */
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

    /**
     * Parses an {@code item_source} argument.
     *
     * <p>Phase 3 collapses the v1 {@code source} / {@code item} / {@code item_source} aliases into the
     * single name {@code item_source}; the legacy converter rewrites old configuration.</p>
     *
     * @param raw raw argument value
     * @return the parsed source, or {@code null} when it cannot be parsed
     */
    public static ItemSource itemSource(String raw) {
        return ItemSourceUtil.parse(Texts.trim(raw));
    }

    /** {@return the shorthand form of {@code source}, never {@code null}} */
    public static String shorthand(ItemSource source) {
        return Texts.toStringSafe(ItemSourceUtil.toShorthand(source));
    }

    /**
     * Parses an inventory slot reference.
     *
     * <p>Accepts the same names as v1 plus {@code slot_N} / {@code hotbar_N} indices in {@code [0, 35]}.
     * v1 carried two independent copies of this table ({@code ActionInventorySlot} and the
     * {@code SlotRef} nested inside {@code ClearItemAction}); v2 keeps one.</p>
     *
     * @param raw raw argument value
     * @param fallback value used when {@code raw} is blank
     * @return the slot, or {@code null} when the name is not supported
     */
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

    /** {@return whether {@code itemStack} is absent or air} */
    public static boolean isEmpty(ItemStack itemStack) {
        return itemStack == null || itemStack.getType().isAir();
    }

    /** {@return {@code raw} resolved as a vanilla material, or {@code null} when unknown} */
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

    /**
     * One player-inventory slot, readable and writable.
     *
     * @param id normalised slot name, used in diagnostics
     * @param getter reads the current stack
     * @param setter writes a stack, treating air as removal
     */
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

    /** Reads one inventory slot. */
    @FunctionalInterface
    public interface Getter {

        ItemStack get(PlayerInventory inventory);
    }

    /** Writes one inventory slot. */
    @FunctionalInterface
    public interface Setter {

        void set(PlayerInventory inventory, ItemStack itemStack);
    }
}
