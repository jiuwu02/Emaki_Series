package emaki.jiuwu.craft.corelib.web.insight;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.plugin.Plugin;

public final class WebInsightAliasRegistry {

    private static final Map<String, RegisteredResolver> RESOLVERS = new LinkedHashMap<>();

    private WebInsightAliasRegistry() {
    }

    public static synchronized void register(Plugin plugin, WebInsightAliasResolver resolver) {
        if (plugin == null || resolver == null) {
            return;
        }
        String idType = normalize(resolver.idType());
        if (idType.isBlank()) {
            return;
        }
        RESOLVERS.put(idType, new RegisteredResolver(plugin, resolver));
    }

    public static synchronized void unregister(Plugin plugin) {
        if (plugin == null) {
            return;
        }
        RESOLVERS.entrySet().removeIf(entry -> entry.getValue().plugin() == plugin);
    }

    public static synchronized WebInsightAliasResolver.AliasResolution resolve(String idType, String sourceId) {
        RegisteredResolver registered = RESOLVERS.get(normalize(idType));
        if (registered == null || !registered.plugin().isEnabled()) {
            return null;
        }
        WebInsightAliasResolver.AliasResolution resolution = registered.resolver().resolve(sourceId);
        return resolution == null || !resolution.valid() ? null : resolution;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record RegisteredResolver(Plugin plugin, WebInsightAliasResolver resolver) {}
}
