package emaki.jiuwu.craft.skills;

import java.util.Map;

import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.RuntimeComponents;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.skills.bridge.EaBridge;
import emaki.jiuwu.craft.skills.bridge.MythicBridge;
import emaki.jiuwu.craft.skills.config.AppConfig;
import emaki.jiuwu.craft.skills.gui.SkillsGuiService;
import emaki.jiuwu.craft.skills.loader.LocalResourceDefinitionLoader;
import emaki.jiuwu.craft.skills.loader.SkillDefinitionLoader;
import emaki.jiuwu.craft.skills.mythic.MythicSkillCastService;
import emaki.jiuwu.craft.skills.provider.EquipmentSkillCollector;
import emaki.jiuwu.craft.skills.provider.SkillSourceRegistry;
import emaki.jiuwu.craft.skills.service.ActionBarService;
import emaki.jiuwu.craft.skills.service.CastAttemptService;
import emaki.jiuwu.craft.skills.service.CastModeService;
import emaki.jiuwu.craft.skills.service.PlayerSkillDataStore;
import emaki.jiuwu.craft.skills.service.PlayerSkillStateService;
import emaki.jiuwu.craft.skills.service.SkillLevelService;
import emaki.jiuwu.craft.skills.service.SkillParameterResolver;
import emaki.jiuwu.craft.skills.service.SkillRegistryService;
import emaki.jiuwu.craft.skills.service.SkillUpgradeService;
import emaki.jiuwu.craft.skills.trigger.TriggerConflictResolver;
import emaki.jiuwu.craft.skills.trigger.TriggerRegistry;

record SkillsRuntimeComponents(
        YamlConfigLoader<AppConfig> appConfigLoader,
        LanguageLoader languageLoader,
        SkillDefinitionLoader skillDefinitionLoader,
        LocalResourceDefinitionLoader localResourceDefinitionLoader,
        GuiTemplateLoader guiTemplateLoader,
        MessageService messageService,
        BootstrapService bootstrapService,
        GuiService guiService,
        EquipmentSkillCollector equipmentSkillCollector,
        SkillSourceRegistry skillSourceRegistry,
        TriggerRegistry triggerRegistry,
        TriggerConflictResolver triggerConflictResolver,
        SkillRegistryService skillRegistryService,
        PlayerSkillDataStore playerSkillDataStore,
        PlayerSkillStateService playerSkillStateService,
        SkillLevelService skillLevelService,
        SkillParameterResolver skillParameterResolver,
        SkillUpgradeService skillUpgradeService,
        CastModeService castModeService,
        CastAttemptService castAttemptService,
        MythicSkillCastService mythicSkillCastService,
        ActionBarService actionBarService,
        SkillsGuiService skillsGuiService,
        EaBridge eaBridge,
        MythicBridge mythicBridge
) implements RuntimeComponents {

    @Override
    public Map<Class<?>, Object> services() {
        return RuntimeComponents.services(
                RuntimeComponents.component(YamlConfigLoader.class, appConfigLoader),
                RuntimeComponents.component(LanguageLoader.class, languageLoader),
                RuntimeComponents.component(SkillDefinitionLoader.class, skillDefinitionLoader),
                RuntimeComponents.component(LocalResourceDefinitionLoader.class, localResourceDefinitionLoader),
                RuntimeComponents.component(GuiTemplateLoader.class, guiTemplateLoader),
                RuntimeComponents.component(MessageService.class, messageService),
                RuntimeComponents.component(BootstrapService.class, bootstrapService),
                RuntimeComponents.component(GuiService.class, guiService),
                RuntimeComponents.component(EquipmentSkillCollector.class, equipmentSkillCollector),
                RuntimeComponents.component(SkillSourceRegistry.class, skillSourceRegistry),
                RuntimeComponents.component(TriggerRegistry.class, triggerRegistry),
                RuntimeComponents.component(TriggerConflictResolver.class, triggerConflictResolver),
                RuntimeComponents.component(SkillRegistryService.class, skillRegistryService),
                RuntimeComponents.component(PlayerSkillDataStore.class, playerSkillDataStore),
                RuntimeComponents.component(PlayerSkillStateService.class, playerSkillStateService),
                RuntimeComponents.component(SkillLevelService.class, skillLevelService),
                RuntimeComponents.component(SkillParameterResolver.class, skillParameterResolver),
                RuntimeComponents.component(SkillUpgradeService.class, skillUpgradeService),
                RuntimeComponents.component(CastModeService.class, castModeService),
                RuntimeComponents.component(CastAttemptService.class, castAttemptService),
                RuntimeComponents.component(MythicSkillCastService.class, mythicSkillCastService),
                RuntimeComponents.component(ActionBarService.class, actionBarService),
                RuntimeComponents.component(SkillsGuiService.class, skillsGuiService),
                RuntimeComponents.component(EaBridge.class, eaBridge),
                RuntimeComponents.component(MythicBridge.class, mythicBridge)
        );
    }
}
