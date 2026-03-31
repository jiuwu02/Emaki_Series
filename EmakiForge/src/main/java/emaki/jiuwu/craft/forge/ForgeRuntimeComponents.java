package emaki.jiuwu.craft.forge;

import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.forge.loader.AppConfigLoader;
import emaki.jiuwu.craft.forge.loader.BlueprintLoader;
import emaki.jiuwu.craft.forge.loader.GuiTemplateLoader;
import emaki.jiuwu.craft.forge.loader.LanguageLoader;
import emaki.jiuwu.craft.forge.loader.MaterialLoader;
import emaki.jiuwu.craft.forge.loader.PlayerDataStore;
import emaki.jiuwu.craft.forge.loader.RecipeLoader;
import emaki.jiuwu.craft.forge.service.BootstrapService;
import emaki.jiuwu.craft.forge.service.ForgeGuiService;
import emaki.jiuwu.craft.forge.service.ForgeItemRefreshService;
import emaki.jiuwu.craft.forge.service.ForgeService;
import emaki.jiuwu.craft.forge.service.ItemIdentifierService;
import emaki.jiuwu.craft.forge.service.MessageService;
import emaki.jiuwu.craft.forge.service.RecipeBookGuiService;

record ForgeRuntimeComponents(AppConfigLoader appConfigLoader,
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
                              ForgeItemRefreshService itemRefreshService,
                              ForgeService forgeService,
                              ForgeGuiService forgeGuiService,
                              RecipeBookGuiService recipeBookGuiService) {
}
