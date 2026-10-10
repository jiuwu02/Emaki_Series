package emaki.jiuwu.craft.accessory.provider;

import java.lang.reflect.Constructor;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.accessory.service.AccessoryContributionService;
import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.attribute.api.extension.ContributionProviderRegistration;

public final class AccessoryProviderRegistrar {

    private static final String SKILLS_INTEGRATION_CLASS_NAME =
            "emaki.jiuwu.craft.accessory.provider.AccessorySkillsProviderIntegration";

    private final Plugin plugin;
    private final AccessoryContributionService contributionService;
    private final Logger logger;

    private ContributionProviderRegistration attributeRegistration;
    private AccessoryOptionalProviderIntegration skillsIntegration;

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
        return skillsIntegration != null && skillsIntegration.registered();
    }

    public void register() {
        if (attributeRegistration == null && EmakiAttributeApi.status().usable()) {
            attributeRegistration = EmakiAttributeApi.extensions().registerContributionProvider(
                    plugin, new AccessoryAttributeProvider(contributionService, logger));
        }
        if (skillsIntegration == null && Bukkit.getPluginManager().isPluginEnabled("EmakiSkills")) {
            skillsIntegration = createSkillsIntegration();
        }
    }

    public void unregister() {
        if (attributeRegistration != null) {
            attributeRegistration.close();
            attributeRegistration = null;
        }
        if (skillsIntegration != null) {
            skillsIntegration.close();
            skillsIntegration = null;
        }
    }

    private AccessoryOptionalProviderIntegration createSkillsIntegration() {
        try {
            Class<?> integrationType = Class.forName(
                    SKILLS_INTEGRATION_CLASS_NAME, true, AccessoryProviderRegistrar.class.getClassLoader());
            Constructor<?> constructor = integrationType.getConstructor(
                    Plugin.class, AccessoryContributionService.class, Logger.class);
            Object value = constructor.newInstance(plugin, contributionService, logger);
            if (value instanceof AccessoryOptionalProviderIntegration integration) {
                return integration;
            }
            logger.warning("[integration] EmakiSkills 集成实现类型无效");
        } catch (ClassNotFoundException | LinkageError exception) {
            logger.info("[integration] EmakiSkills 集成不可用；将在没有技能贡献的情况下继续");
        } catch (ReflectiveOperationException | SecurityException exception) {
            logger.warning("[integration] EmakiSkills 集成失败: " + exception.getMessage());
        }
        return null;
    }
}
