package emaki.jiuwu.craft.corelib.web.preview;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.plugin.Plugin;

public final class WebItemLayerPreviewRegistry {

    private static final Map<String, RegisteredProvider> PROVIDERS = new LinkedHashMap<>();

    private WebItemLayerPreviewRegistry() {
    }

    public static synchronized void register(Plugin plugin, WebItemLayerPreviewProvider provider) {
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

    public static synchronized List<WebItemLayerPreviewProvider> providers() {
        List<WebItemLayerPreviewProvider> result = new ArrayList<>();
        for (RegisteredProvider registered : PROVIDERS.values()) {
            if (registered.plugin().isEnabled()) {
                result.add(registered.provider());
            }
        }
        result.sort(Comparator.comparingInt(WebItemLayerPreviewProvider::order).thenComparing(provider -> normalize(provider.id())));
        return result;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record RegisteredProvider(Plugin plugin, WebItemLayerPreviewProvider provider) {}
}
