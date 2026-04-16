package emaki.jiuwu.craft.forge.service;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceType;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;

public final class ItemIdentifierService {

    private final EmakiForgePlugin plugin;
    private final ItemSourceService itemSourceService;
    private final Set<String> unavailableSourceWarnings = new LinkedHashSet<>();

    public ItemIdentifierService(EmakiForgePlugin plugin, ItemSourceService itemSourceService) {
        this.plugin = plugin;
        this.itemSourceService = itemSourceService;
        refresh();
    }

    public void refresh() {
        unavailableSourceWarnings.clear();
    }

    public ItemSource identifyItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return null;
        }
        ItemSourceService itemSourceService = coreItemSourceService();
        if (itemSourceService == null) {
            return new ItemSource(ItemSourceType.VANILLA, itemStack.getType().name());
        }
        ItemSource identified = itemSourceService.identifyItem(itemStack);
        return identified != null ? identified : new ItemSource(ItemSourceType.VANILLA, itemStack.getType().name());
    }

    public boolean matchesSource(ItemStack itemStack, ItemSource source) {
        if (!ensureSourceAvailable(source, "runtime-match")) {
            return false;
        }
        ItemSource identified = identifyItem(itemStack);
        return ItemSourceUtil.matches(identified, source);
    }

    public ItemStack createItem(ItemSource source, int amount) {
        if (source == null || source.getType() == null || Texts.isBlank(source.getIdentifier())) {
            return null;
        }
        if (!ensureSourceAvailable(source, "runtime-create")) {
            return null;
        }
        ItemSourceService itemSourceService = coreItemSourceService();
        return itemSourceService == null ? null : itemSourceService.createItem(source, amount);
    }

    public String displayName(ItemSource source) {
        if (source == null || source.getType() == null || Texts.isBlank(source.getIdentifier())) {
            return "";
        }
        ItemSourceService itemSourceService = coreItemSourceService();
        if (itemSourceService == null) {
            String shorthand = ItemSourceUtil.toShorthand(source);
            return Texts.isBlank(shorthand) ? source.getIdentifier() : shorthand;
        }
        return itemSourceService.displayName(source);
    }

    public void validateConfiguredSource(ItemSource source, String location) {
        ensureSourceAvailable(source, location);
    }

    private ItemSourceService coreItemSourceService() {
        return itemSourceService;
    }

    private boolean ensureSourceAvailable(ItemSource source, String context) {
        if (source == null || source.getType() == null) {
            return false;
        }
        ItemSourceService itemSourceService = coreItemSourceService();
        if (itemSourceService == null) {
            logUnavailableSource(source, "EmakiCoreLib", context, "EmakiCoreLib item source service is unavailable.");
            return false;
        }
        boolean available = itemSourceService.isAvailable(source);
        if (!available) {
            logUnavailableSource(
                    source,
                    source.getType().displayName(),
                    context,
                    "CoreLib item source bridge is unavailable or the source registry has not finished loading."
            );
        }
        return available;
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
}
