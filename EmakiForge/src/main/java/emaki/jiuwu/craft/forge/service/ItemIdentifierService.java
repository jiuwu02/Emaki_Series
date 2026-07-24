package emaki.jiuwu.craft.forge.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceProbe;
import emaki.jiuwu.craft.corelib.item.ItemSourceProbeStatus;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceType;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;

public final class ItemIdentifierService {

    public record SourceProbe(ItemSource source,
            ItemSourceProbeStatus status,
            String provider,
            String detail) {

        public SourceProbe {
            status = status == null ? ItemSourceProbeStatus.RESOLUTION_ERROR : status;
            provider = Texts.toStringSafe(provider);
            detail = Texts.toStringSafe(detail);
        }

        public boolean ready() {
            return status == ItemSourceProbeStatus.READY;
        }

        public boolean capabilityIssue() {
            return status == ItemSourceProbeStatus.RESOLVER_MISSING
                    || status == ItemSourceProbeStatus.PROVIDER_NOT_READY
                    || status == ItemSourceProbeStatus.INCOMPATIBLE;
        }
    }

    private final EmakiForgePlugin plugin;
    private final ItemSourceService itemSourceService;

    public ItemIdentifierService(EmakiForgePlugin plugin, ItemSourceService itemSourceService) {
        this.plugin = plugin;
        this.itemSourceService = itemSourceService;
    }

    public void refresh() {
        // ItemSourceService owns the authoritative resolver snapshot. No local cache is required.
    }

    public ItemSource parseSource(Object raw) {
        return ItemSourceUtil.parse(raw);
    }

    public boolean matches(ItemStack itemStack, ItemSource source) {
        if (itemSourceService == null || itemStack == null || source == null) {
            return false;
        }
        return source.equals(itemSourceService.identifyItem(itemStack));
    }

    public String identify(ItemStack itemStack) {
        return toShorthand(identifySource(itemStack));
    }

    public ItemSource identifySource(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || itemSourceService == null) {
            return null;
        }
        return itemSourceService.identifyItem(itemStack);
    }

    public ItemSource identifyItem(ItemStack itemStack) {
        return identifySource(itemStack);
    }

    public String toShorthand(ItemSource source) {
        return ItemSourceUtil.toShorthand(source);
    }

    public String displayName(ItemSource source) {
        if (source == null) {
            return "";
        }
        return itemSourceService == null ? source.getIdentifier() : itemSourceService.displayName(source);
    }

    public ItemStack createItem(Object raw, int amount) {
        return createItem(parseSource(raw), amount);
    }

    public ItemStack createItem(ItemSource source, int amount) {
        if (source == null || amount <= 0 || itemSourceService == null) {
            return null;
        }
        ItemStack item = itemSourceService.createItem(source, Math.max(1, amount));
        if (item != null && !item.getType().isAir()) {
            return item;
        }
        if (source.getType() != ItemSourceType.VANILLA) {
            warnResolutionFailure("console.item_source_resolve_failed", source, "create");
        }
        return null;
    }

    public boolean isConfiguredSourceAvailable(ItemSource source) {
        return probeSource(source).ready();
    }

    public SourceProbe probeSource(ItemSource source) {
        return probeSource(source, "");
    }

    public SourceProbe probeSource(ItemSource source, String location) {
        if (source == null) {
            return new SourceProbe(null, ItemSourceProbeStatus.INVALID_SOURCE, "", "Item source is invalid.");
        }
        if (itemSourceService == null) {
            return new SourceProbe(source, ItemSourceProbeStatus.RESOLVER_MISSING, "EmakiCoreLib",
                    "ItemSourceService is unavailable.");
        }
        ItemSourceProbe probe = itemSourceService.probe(source);
        if (probe == null) {
            return new SourceProbe(source, ItemSourceProbeStatus.RESOLUTION_ERROR, "EmakiCoreLib",
                    "ItemSourceService returned no probe result.");
        }
        String detail = probe.detail();
        if (!Texts.isBlank(location) && !probe.ready()) {
            detail = (Texts.isBlank(detail) ? "" : detail + " ") + "Location: " + location;
        }
        return new SourceProbe(source, probe.status(), probe.resolverId(), detail);
    }

    public void validateConfiguredSource(ItemSource source, String location) {
        SourceProbe probe = probeSource(source, location);
        if (probe.ready()) {
            return;
        }
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("source", toShorthand(source));
        replacements.put("location", Texts.toStringSafe(location));
        replacements.put("provider", probe.provider());
        replacements.put("status", probe.status().name());
        replacements.put("detail", probe.detail());
        if (plugin != null && plugin.messageService() != null) {
            plugin.messageService().warning("console.item_source_unavailable", replacements);
        }
    }

    private void warnResolutionFailure(String messageKey, ItemSource source, String operation) {
        if (plugin == null || plugin.messageService() == null || source == null) {
            return;
        }
        plugin.messageService().warning(messageKey, Map.of(
                "source", ItemSourceUtil.toShorthand(source),
                "operation", Texts.toStringSafe(operation)
        ));
    }

    public Material vanillaMaterial(ItemSource source) {
        if (source == null || source.getType() != ItemSourceType.VANILLA) {
            return null;
        }
        return Material.matchMaterial(source.getIdentifier());
    }
}
