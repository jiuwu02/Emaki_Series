package emaki.jiuwu.craft.accessory.persistence;

import java.io.File;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public final class AccessoryDataFile {

    public static final int FORMAT_VERSION = 2;

    private static final String KEY_FORMAT_VERSION = "format_version";
    private static final String KEY_PLAYER_NAME = "player_name";
    private static final String KEY_ENABLED_PAGE = "enabled_page";
    private static final String KEY_PAGES = "pages";
    private static final String LEGACY_KEY_SLOTS = "slots";

    private final Logger logger;
    private final Path dataRoot;
    private final Supplier<String> defaultPageSupplier;

    public AccessoryDataFile(Logger logger, Path dataRoot, Supplier<String> defaultPageSupplier) {
        this.logger = logger;
        this.dataRoot = dataRoot;
        this.defaultPageSupplier = defaultPageSupplier;
    }

    public File fileFor(UUID playerId) {
        return dataRoot.resolve(playerId + ".yml").toFile();
    }

    public Path dataRoot() {
        return dataRoot;
    }

    public boolean recognized(YamlSection root) {
        if (root == null || root.isEmpty()) {
            return true;
        }
        if (root.getSection(KEY_PAGES) != null) {
            return true;
        }
        if (root.getSection(LEGACY_KEY_SLOTS) == null) {
            return false;
        }
        return Texts.isNotBlank(defaultPage());
    }

    public PlayerAccessories read(UUID playerId, String playerName, YamlSection root) {
        PlayerAccessories accessories = new PlayerAccessories(playerId);
        if (root == null || root.isEmpty()) {
            accessories.installLoaded(Map.of(), defaultPage());
            applyName(accessories, playerName, "");
            return accessories;
        }
        String storedName = root.getString(KEY_PLAYER_NAME, "");
        YamlSection pages = root.getSection(KEY_PAGES);
        if (pages != null) {
            accessories.installLoaded(
                    readPages(playerId, pages),
                    root.getString(KEY_ENABLED_PAGE, defaultPage()));
            applyName(accessories, playerName, storedName);
            return accessories;
        }
        YamlSection legacySlots = root.getSection(LEGACY_KEY_SLOTS);
        String migratedPage = defaultPage();
        if (legacySlots != null && Texts.isBlank(migratedPage)) {
            warn("Cannot migrate accessory data for " + playerId
                    + " because no accessory page is configured; the file is left untouched");
            accessories.installLoaded(Map.of(), "");
            applyName(accessories, playerName, storedName);
            return accessories;
        }
        if (legacySlots != null) {
            Map<String, ItemStack> migrated = readSlots(playerId, legacySlots);
            accessories.installLoaded(
                    migrated.isEmpty() ? Map.of() : Map.of(migratedPage, migrated),
                    migratedPage);
            applyName(accessories, playerName, storedName);
            info("Migrated accessory data for " + playerId + " from format 1 to " + FORMAT_VERSION
                    + ": " + migrated.size() + " item(s) moved to page " + migratedPage);
            return accessories;
        }
        warn("Unrecognized accessory data structure for " + playerId
                + "; keeping the file untouched and starting from an empty session");
        accessories.installLoaded(Map.of(), defaultPage());
        applyName(accessories, playerName, storedName);
        return accessories;
    }

    public Map<String, Object> write(PlayerAccessories accessories) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(KEY_FORMAT_VERSION, FORMAT_VERSION);
        root.put(KEY_PLAYER_NAME, accessories == null ? "" : accessories.playerName());
        root.put(KEY_ENABLED_PAGE, accessories == null ? defaultPage() : accessories.enabledPage());
        Map<String, Object> pages = new LinkedHashMap<>();
        if (accessories != null) {
            accessories.allPages().forEach((pageId, items) -> {
                Map<String, Object> encoded = new LinkedHashMap<>();
                items.forEach((slotInstanceId, item) -> {
                    if (item != null && !item.getType().isAir()) {
                        encoded.put(slotInstanceId,
                                Base64.getEncoder().encodeToString(item.serializeAsBytes()));
                    }
                });
                if (!encoded.isEmpty()) {
                    pages.put(pageId, encoded);
                }
            });
        }
        root.put(KEY_PAGES, pages);
        return root;
    }

    private Map<String, Map<String, ItemStack>> readPages(UUID playerId, YamlSection pages) {
        Map<String, Map<String, ItemStack>> loaded = new LinkedHashMap<>();
        for (String pageKey : pages.getKeys(false)) {
            String pageId = Texts.normalizeId(pageKey);
            YamlSection slots = pages.getSection(pageKey);
            if (Texts.isBlank(pageId) || slots == null) {
                continue;
            }
            Map<String, ItemStack> items = readSlots(playerId, slots);
            if (!items.isEmpty()) {
                loaded.put(pageId, items);
            }
        }
        return loaded;
    }

    private Map<String, ItemStack> readSlots(UUID playerId, YamlSection slots) {
        Map<String, ItemStack> items = new LinkedHashMap<>();
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
        return items;
    }

    private void applyName(PlayerAccessories accessories, String playerName, String storedName) {
        accessories.clearDirty();
        if (Texts.isNotBlank(storedName)) {
            accessories.playerName(storedName);
        } else if (Texts.isNotBlank(playerName)) {
            accessories.playerName(playerName);
        }
        accessories.clearDirty();
    }

    private String defaultPage() {
        if (defaultPageSupplier == null) {
            return "";
        }
        return Texts.normalizeId(defaultPageSupplier.get());
    }

    private ItemStack decode(UUID playerId, String slotInstanceId, String encoded) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
        } catch (RuntimeException exception) {
            warn("Dropped undecodable accessory in slot " + slotInstanceId
                    + " for player " + playerId + ": " + Texts.toStringSafe(exception.getMessage()));
            return null;
        }
    }

    private void info(String message) {
        if (logger != null) {
            logger.info(message);
        }
    }

    private void warn(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }
}
