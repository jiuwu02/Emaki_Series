package emaki.jiuwu.craft.corelib.item.preview;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.plugin.Plugin;

public final class ItemLayerPreviewRegistry {

    private static final Map<String, RegisteredProvider> PROVIDERS = new LinkedHashMap<>();

    private ItemLayerPreviewRegistry() {
    }

    public static synchronized void register(Plugin plugin, ItemLayerPreviewProvider provider) {
        if (plugin == null || provider == null) {
            return;
        }
        String id = normalize(provider.id());
        if (id.isBlank()) {
            return;
        }
        PROVIDERS.put(id, new RegisteredProvider(plugin, provider));
    }

    public static synchronized void unregister(Plugin plugin) {
        if (plugin == null) {
            return;
        }
        PROVIDERS.entrySet().removeIf(entry -> entry.getValue().plugin() == plugin);
    }

    public static synchronized List<ItemLayerPreviewProvider> providers() {
        List<ItemLayerPreviewProvider> result = new ArrayList<>();
        for (RegisteredProvider registered : PROVIDERS.values()) {
            if (registered.plugin().isEnabled()) {
                result.add(registered.provider());
            }
        }
        result.sort(Comparator.comparingInt(ItemLayerPreviewProvider::order).thenComparing(provider -> normalize(provider.id())));
        return result;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record RegisteredProvider(Plugin plugin, ItemLayerPreviewProvider provider) {}
}
