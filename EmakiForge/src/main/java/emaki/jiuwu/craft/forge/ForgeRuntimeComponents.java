package emaki.jiuwu.craft.forge;

import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.RuntimeComponents;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.forge.loader.BlueprintLoader;
import emaki.jiuwu.craft.forge.loader.MaterialLoader;
import emaki.jiuwu.craft.forge.loader.PlayerDataStore;
import emaki.jiuwu.craft.forge.loader.RecipeLoader;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.forge.service.ForgeGuiService;
import emaki.jiuwu.craft.forge.service.ForgeItemRefreshService;
import emaki.jiuwu.craft.forge.service.ForgeService;
import emaki.jiuwu.craft.forge.service.ItemIdentifierService;
import emaki.jiuwu.craft.forge.service.RecipeBookGuiService;
import emaki.jiuwu.craft.forge.config.AppConfig;
import emaki.jiuwu.craft.corelib.integration.ReflectivePdcAttributeGateway;

record ForgeRuntimeComponents(YamlConfigLoader<AppConfig> appConfigLoader,
        LanguageLoader languageLoader,
        BlueprintLoader blueprintLoader,
        MaterialLoader materialLoader,
        RecipeLoader recipeLoader,
        GuiTemplateLoader guiTemplateLoader,
        PlayerDataStore playerDataStore,
        MessageService messageService,
        BootstrapService bootstrapService,
        GuiService guiService,
        ItemIdentifierService itemIdentifierService,
        ReflectivePdcAttributeGateway pdcAttributeGateway,
        ForgeItemRefreshService itemRefreshService,
        ForgeService forgeService,
        ForgeGuiService forgeGuiService,
        RecipeBookGuiService recipeBookGuiService) implements RuntimeComponents {
}
