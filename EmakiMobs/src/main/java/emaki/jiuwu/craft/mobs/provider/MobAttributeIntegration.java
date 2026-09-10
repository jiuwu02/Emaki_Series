package emaki.jiuwu.craft.mobs.provider;

import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.attribute.api.extension.ContributionProviderRegistration;
import emaki.jiuwu.craft.mobs.loader.MobSpec;
import emaki.jiuwu.craft.mobs.service.MobIdentifier;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.function.Supplier;

public final class MobAttributeIntegration implements MobOptionalProviderIntegration {

    private final Plugin plugin;
    private final MobIdentifier mobIdentifier;
    private final Supplier<Map<String, MobSpec>> registry;

    private ContributionProviderRegistration registration;

    public MobAttributeIntegration(Plugin plugin,
                                   MobIdentifier mobIdentifier,
                                   Supplier<Map<String, MobSpec>> registry) {
        this.plugin = plugin;
        this.mobIdentifier = mobIdentifier;
        this.registry = registry;
    }

    @Override
    public boolean registered() {
        return registration != null;
    }

    @Override
    public void register() {
        if (registration != null || !EmakiAttributeApi.status().usable()) {
            return;
        }
        registration = EmakiAttributeApi.extensions().registerContributionProvider(
                plugin, new MobAttributeProvider(mobIdentifier, registry));
    }

    @Override
    public void close() {
        if (registration != null) {
            registration.close();
            registration = null;
        }
    }
}
