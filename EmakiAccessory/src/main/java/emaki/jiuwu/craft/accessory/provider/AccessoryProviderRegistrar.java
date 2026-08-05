package emaki.jiuwu.craft.accessory.provider;

import java.util.logging.Logger;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.accessory.service.AccessoryContributionService;
import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.attribute.api.extension.ContributionProviderRegistration;
import emaki.jiuwu.craft.skills.api.EmakiSkillsApi;
import emaki.jiuwu.craft.skills.api.SkillSourceRegistration;

/**
 * Registers and revokes the two contribution providers.
 *
 * <p>Registration is skipped when the host plugin is absent, which is the whole degradation story: with
 * neither EmakiAttribute nor EmakiSkills installed the module remains a working container that stores
 * items and grants nothing. Nothing else in the module has to test for their presence.
 *
 * <p>Both handles are closed on disable. Closing is idempotent and a superseded handle never removes its
 * replacement, so a reload that re-registers cannot leave the host pointing at a dead provider.
 */
public final class AccessoryProviderRegistrar {

    private final Plugin plugin;
    private final AccessoryContributionService contributionService;
    private final Logger logger;

    private ContributionProviderRegistration attributeRegistration;
    private SkillSourceRegistration skillRegistration;

    /**
     * Creates the registrar.
     *
     * @param plugin              the owning plugin, used as the registration owner
     * @param contributionService the snapshot cache both providers read
     * @param logger              receives provider failure reports
     */
    public AccessoryProviderRegistrar(Plugin plugin,
            AccessoryContributionService contributionService,
            Logger logger) {
        this.plugin = plugin;
        this.contributionService = contributionService;
        this.logger = logger;
    }

    /** {@return whether the attribute provider is currently registered} */
    public boolean attributeRegistered() {
        return attributeRegistration != null;
    }

    /** {@return whether the skill provider is currently registered} */
    public boolean skillRegistered() {
        return skillRegistration != null;
    }

    /** Registers both providers with whichever hosts are installed. */
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

    /** Revokes both registrations. */
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
