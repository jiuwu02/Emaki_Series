package emaki.jiuwu.craft.accessory.provider;

import java.util.logging.Logger;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.accessory.service.AccessoryContributionService;
import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.attribute.api.extension.ContributionProviderRegistration;
import emaki.jiuwu.craft.skills.api.EmakiSkillsApi;
import emaki.jiuwu.craft.skills.api.SkillSourceRegistration;

public final class AccessoryProviderRegistrar {

    private final Plugin plugin;
    private final AccessoryContributionService contributionService;
    private final Logger logger;

    private ContributionProviderRegistration attributeRegistration;
    private SkillSourceRegistration skillRegistration;

    public AccessoryProviderRegistrar(Plugin plugin,
            AccessoryContributionService contributionService,
            Logger logger) {
        this.plugin = plugin;
        this.contributionService = contributionService;
        this.logger = logger;
    }

    public boolean attributeRegistered() {
        return attributeRegistration != null;
    }

    public boolean skillRegistered() {
        return skillRegistration != null;
    }

    public void register() {
        if (attributeRegistration == null && EmakiAttributeApi.status().usable()) {
            attributeRegistration = EmakiAttributeApi.extensions().registerContributionProvider(
                    plugin, new AccessoryAttributeProvider(contributionService, logger));
        }
        if (skillRegistration == null && EmakiSkillsApi.status().usable()) {
            skillRegistration = EmakiSkillsApi.extensions().registerSkillSource(
                    plugin, new AccessorySkillProvider(contributionService, logger));
        }
    }

    public void unregister() {
        if (attributeRegistration != null) {
            attributeRegistration.close();
            attributeRegistration = null;
        }
        if (skillRegistration != null) {
            skillRegistration.close();
            skillRegistration = null;
        }
    }
}
