package emaki.jiuwu.craft.accessory.provider;

import java.util.logging.Logger;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.accessory.service.AccessoryContributionService;
import emaki.jiuwu.craft.skills.api.EmakiSkillsApi;
import emaki.jiuwu.craft.skills.api.SkillSourceRegistration;

public final class AccessorySkillsProviderIntegration implements AccessoryOptionalProviderIntegration {

    private final SkillSourceRegistration registration;
    private final boolean registered;

    public AccessorySkillsProviderIntegration(Plugin plugin,
            AccessoryContributionService contributionService,
            Logger logger) {
        if (!EmakiSkillsApi.status().usable()) {
            registration = SkillSourceRegistration.noop();
            registered = false;
            return;
        }
        registration = EmakiSkillsApi.extensions().registerSkillSource(
                plugin, new AccessorySkillProvider(contributionService, logger));
        registered = true;
    }

    @Override
    public boolean registered() {
        return registered;
    }

    @Override
    public void close() {
        registration.close();
    }
}
