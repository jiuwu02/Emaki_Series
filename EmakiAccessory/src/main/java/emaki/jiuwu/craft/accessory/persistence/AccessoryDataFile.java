package emaki.jiuwu.craft.accessory.persistence;

import java.io.File;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

/**
 * Per-player accessory persistence: one YAML file holding {@code slotInstanceId -> Base64 item}.
 *
 * <p>Items are encoded with {@link ItemStack#serializeAsBytes()} rather than the map-based
 * {@code ItemStack#serialize()}, because the byte form records the game's {@code DataVersion} and runs
 * Mojang's DataFixer on read, so a saved accessory survives a Minecraft upgrade. YAML cannot hold raw
 * bytes, hence Base64 around that payload.
 *
 * <p>Decoding is per slot and failure is contained: a slot whose payload cannot be decoded is dropped
 * with a warning and every other slot in the file still loads. Framing each item separately is the
 * whole reason not to serialise the map as one blob.
 */
public final class AccessoryDataFile {

    /** Bumped only when the on-disk layout changes in a way a reader must branch on. */
    public static final int FORMAT_VERSION = 1;

    private static final String KEY_FORMAT_VERSION = "format_version";
    private static final String KEY_PLAYER_NAME = "player_name";
    private static final String KEY_SLOTS = "slots";

    private final Logger logger;
    private final Path dataRoot;

    /**
     * Creates the file accessor.
     *
     * @param logger   receives per-slot decode warnings
     * @param dataRoot directory holding one file per player
     */
    public AccessoryDataFile(Logger logger, Path dataRoot) {
        this.logger = logger;
        this.dataRoot = dataRoot;
    }

    /**
     * Resolves one player's data file.
     *
     * @param playerId the player id
     * @return the file, which may not exist yet
     */
    public File fileFor(UUID playerId) {
        return dataRoot.resolve(playerId + ".yml").toFile();
    }

    /** {@return the directory holding player files} */
    public Path dataRoot() {
        return dataRoot;
    }

    /**
     * Converts a loaded YAML document into a payload.
     *
     * @param playerId   the player id
     * @param playerName the name to record when the file carries none
     * @param root       the loaded document; {@code null} or empty yields an empty payload
     * @return the payload, never dirty
     */
    public PlayerAccessories read(UUID playerId, String playerName, YamlSection root) {
        PlayerAccessories accessories = new PlayerAccessories(playerId);
        Map<String, ItemStack> items = new LinkedHashMap<>();
        if (root != null && !root.isEmpty()) {
            YamlSection slots = root.getSection(KEY_SLOTS);
            if (slots != null) {
                for (String key : slots.getKeys(false)) {
                    String slotInstanceId = Texts.normalizeId(key);
                    String encoded = slots.getString(key, "");
                    if (Texts.isBlank(slotInstanceId) || Texts.isBlank(encoded)) {
                        continue;
                    }
                    ItemStack decoded = decode(playerId, slotInstanceId, encoded);
                    if (decoded != null) {
                        items.put(slotInstanceId, decoded);
                    }
                }
            }
            String storedName = root.getString(KEY_PLAYER_NAME, "");
            accessories.installLoaded(items);
            accessories.clearDirty();
            if (Texts.isNotBlank(storedName)) {
                accessories.playerName(storedName);
            } else if (Texts.isNotBlank(playerName)) {
                accessories.playerName(playerName);
            }
            accessories.clearDirty();
            return accessories;
        }
        accessories.installLoaded(items);
        if (Texts.isNotBlank(playerName)) {
            accessories.playerName(playerName);
        }
        accessories.clearDirty();
        return accessories;
    }

    /**
     * Renders a payload into the map form the YAML writer expects.
     *
     * @param accessories the payload snapshot to write
     * @return the values to serialise
     */
    public Map<String, Object> write(PlayerAccessories accessories) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(KEY_FORMAT_VERSION, FORMAT_VERSION);
        root.put(KEY_PLAYER_NAME, accessories == null ? "" : accessories.playerName());
        Map<String, Object> slots = new LinkedHashMap<>();
        if (accessories != null) {
            accessories.items().forEach((slotInstanceId, item) -> {
                if (item != null && !item.getType().isAir()) {
                    slots.put(slotInstanceId, Base64.getEncoder().encodeToString(item.serializeAsBytes()));
                }
            });
        }
        root.put(KEY_SLOTS, slots);
        return root;
    }

    private ItemStack decode(UUID playerId, String slotInstanceId, String encoded) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
        } catch (RuntimeException exception) {
            if (logger != null) {
                logger.warning("Dropped undecodable accessory in slot " + slotInstanceId
                        + " for player " + playerId + ": " + Texts.toStringSafe(exception.getMessage()));
            }
            return null;
        }
    }
}
