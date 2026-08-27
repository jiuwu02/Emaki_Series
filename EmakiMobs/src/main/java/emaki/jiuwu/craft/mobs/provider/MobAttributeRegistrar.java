package emaki.jiuwu.craft.mobs.provider;

import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.attribute.api.extension.ContributionProviderRegistration;
import emaki.jiuwu.craft.mobs.loader.MobSpec;
import emaki.jiuwu.craft.mobs.service.MobIdentifier;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.function.Supplier;

public final class MobAttributeRegistrar {

    private final Plugin plugin;
    private final MobIdentifier mobIdentifier;
    private final Supplier<Map<String, MobSpec>> registry;

    private ContributionProviderRegistration registration;

    public MobAttributeRegistrar(Plugin plugin,
                                 MobIdentifier mobIdentifier,
                                 Supplier<Map<String, MobSpec>> registry) {
        this.plugin = plugin;
        this.mobIdentifier = mobIdentifier;
        this.registry = registry;
    }

    public boolean registered() {
        return registration != null;
    }

    public void register() {
        if (registration != null || !EmakiAttributeApi.status().usable()) {
            return;
        }
        registration = EmakiAttributeApi.extensions().registerContributionProvider(
                plugin, new MobAttributeProvider(mobIdentifier, registry));
    }

    public void unregister() {
        if (registration != null) {
            registration.close();
            registration = null;
        }
    }
}
