package emaki.jiuwu.craft.forge.service;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceResolver;
import emaki.jiuwu.craft.corelib.item.ItemSourceType;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.item.CustomItem;
import net.momirealms.craftengine.core.item.ItemBuildContext;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import pers.neige.neigeitems.manager.ItemManager;

public final class ItemIdentifierService implements ItemSourceResolver {

    private final EmakiForgePlugin plugin;
    private final Set<String> unavailableSourceWarnings = new LinkedHashSet<>();
    private Plugin mmoItemsPlugin;
    private Plugin neigeItemsPlugin;
    private Plugin craftEnginePlugin;
    private ExternalItemSupport mmoItemsSupport;
    private ExternalItemSupport neigeItemsSupport;
    private ExternalItemSupport craftEngineSupport;

    public ItemIdentifierService(EmakiForgePlugin plugin) {
        this.plugin = plugin;
        refresh();
    }

    public void refresh() {
        unavailableSourceWarnings.clear();
        mmoItemsPlugin = Bukkit.getPluginManager().getPlugin("MMOItems");
        neigeItemsPlugin = Bukkit.getPluginManager().getPlugin("NeigeItems");
        craftEnginePlugin = Bukkit.getPluginManager().getPlugin("CraftEngine");
        mmoItemsSupport = buildMmoItemsSupport();
        neigeItemsSupport = buildNeigeItemsSupport();
        craftEngineSupport = buildCraftEngineSupport();
    }

    @Override
    public String id() {
        return "forge_item_identifier";
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean supports(ItemSource source) {
        return source != null && source.getType() != null;
    }

    public ItemSource identifyItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return null;
        }
        ItemSource neige = identifyNeigeItems(itemStack);
        if (neige != null) {
            return neige;
        }
        ItemSource craftEngine = identifyCraftEngine(itemStack);
        if (craftEngine != null) {
            return craftEngine;
        }
        ItemSource mmoItems = identifyMmoItems(itemStack);
        if (mmoItems != null) {
            return mmoItems;
        }
        return new ItemSource(ItemSourceType.VANILLA, itemStack.getType().name());
    }

    @Override
    public ItemSource identify(ItemStack itemStack) {
        return identifyItem(itemStack);
    }

    public boolean matchesSource(ItemStack itemStack, ItemSource source) {
        if (!ensureSourceAvailable(source, "runtime-match")) {
            return false;
        }
        ItemSource identified = identifyItem(itemStack);
        return identified != null && ItemSourceUtil.matches(identified, source);
    }

    public ItemStack createItem(ItemSource source, int amount) {
        if (source == null || source.getType() == null || Texts.isBlank(source.getIdentifier())) {
            return null;
        }
        if (!ensureSourceAvailable(source, "runtime-create")) {
            return null;
        }
        return switch (source.getType()) {
            case MMOITEMS -> createMmoItemsItem(source.getIdentifier(), amount);
            case NEIGEITEMS -> createNeigeItemsItem(source.getIdentifier(), amount);
            case CRAFTENGINE -> createCraftEngineItem(source.getIdentifier(), amount);
            case VANILLA -> createVanillaItem(source.getIdentifier(), amount);
        };
    }

    @Override
    public ItemStack create(ItemSource source, int amount) {
        return createItem(source, amount);
    }

    public void validateConfiguredSource(ItemSource source, String location) {
        ensureSourceAvailable(source, location);
    }

    private ItemSource identifyMmoItems(ItemStack itemStack) {
        return mmoItemsSupport == null ? null : mmoItemsSupport.identify(itemStack);
    }

    private ItemSource identifyNeigeItems(ItemStack itemStack) {
        return neigeItemsSupport == null ? null : neigeItemsSupport.identify(itemStack);
    }

    private ItemSource identifyCraftEngine(ItemStack itemStack) {
        return craftEngineSupport == null ? null : craftEngineSupport.identify(itemStack);
    }

    private ItemStack createNeigeItemsItem(String identifier, int amount) {
        return neigeItemsSupport == null ? null : neigeItemsSupport.create(identifier, amount);
    }

    private ItemStack createMmoItemsItem(String identifier, int amount) {
        return mmoItemsSupport == null ? null : mmoItemsSupport.create(identifier, amount);
    }

    private ItemStack createCraftEngineItem(String identifier, int amount) {
        return craftEngineSupport == null ? null : craftEngineSupport.create(identifier, amount);
    }

    private ItemStack createVanillaItem(String identifier, int amount) {
        Material material = resolveMaterial(identifier);
        if (material == null) {
            plugin.messageService().warning("console.item_unknown_vanilla_material_source", Map.of(
                "identifier", identifier
            ));
            return null;
        }
        return new ItemStack(material, Math.max(1, amount));
    }

    private Material resolveMaterial(String identifier) {
        if (Texts.isBlank(identifier)) {
            return null;
        }
        String normalized = identifier.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = normalized.contains(":")
            ? NamespacedKey.fromString(normalized)
            : NamespacedKey.minecraft(normalized);
        return key == null ? null : Registry.MATERIAL.get(key);
    }

    private boolean ensureSourceAvailable(ItemSource source, String context) {
        if (source == null || source.getType() == null) {
            return false;
        }
        return switch (source.getType()) {
            case VANILLA -> true;
            case MMOITEMS -> {
                boolean available = mmoItemsSupport != null;
                if (!available) {
                    logUnavailableSource(source, "MMOItems", context, bridgeUnavailableReason("MMOItems", mmoItemsPlugin));
                }
                yield available;
            }
            case NEIGEITEMS -> {
                boolean available = neigeItemsSupport != null;
                if (!available) {
                    logUnavailableSource(source, "NeigeItems", context, bridgeUnavailableReason("NeigeItems", neigeItemsPlugin));
                }
                yield available;
            }
            case CRAFTENGINE -> {
                boolean available = craftEngineSupport != null;
                if (!available) {
                    logUnavailableSource(source, "CraftEngine", context, bridgeUnavailableReason("CraftEngine", craftEnginePlugin));
                }
                yield available;
            }
        };
    }

    private String bridgeUnavailableReason(String pluginName, Plugin pluginInstance) {
        if (pluginInstance == null) {
            return pluginName + " plugin is not loaded";
        }
        if (!pluginInstance.isEnabled()) {
            return pluginName + " plugin is present but not enabled";
        }
        return pluginName + " API bridge is unavailable";
    }

    private void logUnavailableSource(ItemSource source, String dependency, String context, String reason) {
        String warningKey = dependency + "|" + context + "|" + ItemSourceUtil.toShorthand(source);
        if (!unavailableSourceWarnings.add(warningKey)) {
            return;
        }
        if (plugin.messageService() == null) {
            return;
        }
        plugin.messageService().warning("console.external_item_source_unavailable", Map.of(
            "dependency", dependency,
            "context", context,
            "source", ItemSourceUtil.toShorthand(source),
            "detail", reason
        ));
    }

    private ExternalItemSupport buildMmoItemsSupport() {
        if (mmoItemsPlugin == null || !mmoItemsPlugin.isEnabled()) {
            return null;
        }
        try {
            return new MmoItemsSupport();
        } catch (Throwable error) {
            if (plugin.messageService() != null) {
                plugin.messageService().warning("console.item_bridge_unavailable", Map.of(
                    "dependency", "MMOItems",
                    "error", String.valueOf(error.getMessage())
                ));
            }
            return null;
        }
    }

    private ExternalItemSupport buildNeigeItemsSupport() {
        if (neigeItemsPlugin == null || !neigeItemsPlugin.isEnabled()) {
            return null;
        }
        try {
            return new NeigeItemsSupport();
        } catch (Throwable error) {
            if (plugin.messageService() != null) {
                plugin.messageService().warning("console.item_bridge_unavailable", Map.of(
                    "dependency", "NeigeItems",
                    "error", String.valueOf(error.getMessage())
                ));
            }
            return null;
        }
    }

    private ExternalItemSupport buildCraftEngineSupport() {
        if (craftEnginePlugin == null || !craftEnginePlugin.isEnabled()) {
            return null;
        }
        try {
            return new CraftEngineSupport();
        } catch (Throwable error) {
            if (plugin.messageService() != null) {
                plugin.messageService().warning("console.item_bridge_unavailable", Map.of(
                    "dependency", "CraftEngine",
                    "error", String.valueOf(error.getMessage())
                ));
            }
            return null;
        }
    }

    private interface ExternalItemSupport {

        ItemSource identify(ItemStack itemStack);

        ItemStack create(String identifier, int amount);
    }

    private static final class MmoItemsSupport implements ExternalItemSupport {

        @Override
        public ItemSource identify(ItemStack itemStack) {
            Type type = MMOItems.getType(itemStack);
            String id = MMOItems.getID(itemStack);
            if (type == null || Texts.isBlank(id)) {
                return null;
            }
            return new ItemSource(ItemSourceType.MMOITEMS, type.getId() + ":" + id);
        }

        @Override
        public ItemStack create(String identifier, int amount) {
            MmoItemsKey key = MmoItemsKey.parse(identifier);
            if (key == null) {
                return null;
            }
            ItemStack itemStack = MMOItems.plugin.getItem(key.typeId(), key.itemId());
            if (itemStack == null) {
                itemStack = MMOItems.plugin.getItem(Texts.upper(key.typeId()), key.itemId());
            }
            if (itemStack == null) {
                return null;
            }
            ItemStack cloned = itemStack.clone();
            cloned.setAmount(Math.max(1, amount));
            return cloned;
        }
    }

    private static final class NeigeItemsSupport implements ExternalItemSupport {

        @Override
        public ItemSource identify(ItemStack itemStack) {
            String id = ItemManager.INSTANCE.getItemId(itemStack);
            return Texts.isBlank(id) ? null : new ItemSource(ItemSourceType.NEIGEITEMS, id);
        }

        @Override
        public ItemStack create(String identifier, int amount) {
            ItemStack itemStack = ItemManager.INSTANCE.getItemStack(identifier);
            if (itemStack == null) {
                return null;
            }
            ItemStack cloned = itemStack.clone();
            cloned.setAmount(Math.max(1, amount));
            return cloned;
        }
    }

    private static final class CraftEngineSupport implements ExternalItemSupport {

        @Override
        public ItemSource identify(ItemStack itemStack) {
            Key key = CraftEngineItems.getCustomItemId(itemStack);
            return key == null ? null : new ItemSource(ItemSourceType.CRAFTENGINE, key.asString());
        }

        @Override
        public ItemStack create(String identifier, int amount) {
            CustomItem<ItemStack> customItem = CraftEngineItems.byId(Key.of(identifier));
            if (customItem == null) {
                return null;
            }
            return customItem.buildItemStack(ItemBuildContext.empty(), Math.max(1, amount));
        }
    }

    private record MmoItemsKey(String typeId, String itemId) {

        private static MmoItemsKey parse(String raw) {
            if (Texts.isBlank(raw)) {
                return null;
            }
            String text = Texts.trim(raw);
            int separator = text.indexOf(':');
            if (separator <= 0 || separator >= text.length() - 1) {
                return null;
            }
            String typeId = text.substring(0, separator).trim();
            String itemId = text.substring(separator + 1).trim();
            if (typeId.isEmpty() || itemId.isEmpty()) {
                return null;
            }
            return new MmoItemsKey(typeId, itemId);
        }
    }
}
