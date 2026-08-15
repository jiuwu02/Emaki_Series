package emaki.jiuwu.craft.accessory;

import java.util.Map;

import emaki.jiuwu.craft.accessory.config.AppConfig;
import emaki.jiuwu.craft.accessory.gui.AccessoryGuiService;
import emaki.jiuwu.craft.accessory.loader.AccessoryPartLoader;
import emaki.jiuwu.craft.accessory.loader.AccessorySetLoader;
import emaki.jiuwu.craft.accessory.provider.AccessoryProviderRegistrar;
import emaki.jiuwu.craft.accessory.service.AccessoryAdminService;
import emaki.jiuwu.craft.accessory.service.AccessoryContributionService;
import emaki.jiuwu.craft.accessory.service.AccessorySetService;
import emaki.jiuwu.craft.accessory.service.AccessoryUniqueService;
import emaki.jiuwu.craft.accessory.service.AccessoryWriteSessionRegistry;
import emaki.jiuwu.craft.accessory.service.PlayerAccessoryStore;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.RuntimeComponents;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;

record AccessoryRuntimeComponents(YamlConfigLoader<AppConfig> appConfigLoader,
        LanguageLoader languageLoader,
        MessageService messageService,
        BootstrapService bootstrapService,
        ExecutionDispatcher executionDispatcher,
        ThreadOwnership threadOwnership,
        GuiService guiService,
        GuiTemplateLoader guiTemplateLoader,
        AccessoryPartLoader partLoader,
        AccessorySetLoader setLoader,
        AccessoryUniqueService uniqueService,
        PlayerAccessoryStore accessoryStore,
        AccessorySetService setService,
        AccessoryContributionService contributionService,
        AccessoryGuiService accessoryGuiService,
        AccessoryWriteSessionRegistry writeSessions,
        AccessoryAdminService adminService,
        AccessoryProviderRegistrar providerRegistrar) implements RuntimeComponents {

    @Override
    public Map<Class<?>, Object> services() {
        return RuntimeComponents.services(
                RuntimeComponents.component(MessageService.class, messageService),
                RuntimeComponents.component(LanguageLoader.class, languageLoader),
                RuntimeComponents.component(BootstrapService.class, bootstrapService),
                RuntimeComponents.component(ExecutionDispatcher.class, executionDispatcher),
                RuntimeComponents.component(GuiService.class, guiService),
                RuntimeComponents.component(GuiTemplateLoader.class, guiTemplateLoader),
                RuntimeComponents.component(AccessoryPartLoader.class, partLoader),
                RuntimeComponents.component(AccessorySetLoader.class, setLoader),
                RuntimeComponents.component(PlayerAccessoryStore.class, accessoryStore),
                RuntimeComponents.component(AccessorySetService.class, setService),
                RuntimeComponents.component(AccessoryContributionService.class, contributionService),
                RuntimeComponents.component(AccessoryGuiService.class, accessoryGuiService),
                RuntimeComponents.component(AccessoryAdminService.class, adminService));
    }
}
