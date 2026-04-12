package emaki.jiuwu.craft.forge;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.RuntimeComponents;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.forge.loader.BlueprintLoader;
import emaki.jiuwu.craft.forge.loader.GuiTemplateLoader;
import emaki.jiuwu.craft.forge.loader.MaterialLoader;
import emaki.jiuwu.craft.forge.loader.PlayerDataStore;
import emaki.jiuwu.craft.forge.loader.RecipeLoader;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.forge.service.EditorGuiService;
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
        RecipeBookGuiService recipeBookGuiService,
        EditorGuiService editorGuiService) implements RuntimeComponents {

    @Override
    public Map<Class<?>, Object> services() {
        Map<Class<?>, Object> services = new LinkedHashMap<>();
        services.put(YamlConfigLoader.class, appConfigLoader);
        services.put(LanguageLoader.class, languageLoader);
        services.put(BlueprintLoader.class, blueprintLoader);
        services.put(MaterialLoader.class, materialLoader);
        services.put(RecipeLoader.class, recipeLoader);
        services.put(GuiTemplateLoader.class, guiTemplateLoader);
        services.put(PlayerDataStore.class, playerDataStore);
        services.put(MessageService.class, messageService);
        services.put(BootstrapService.class, bootstrapService);
        services.put(GuiService.class, guiService);
        services.put(ItemIdentifierService.class, itemIdentifierService);
        services.put(ReflectivePdcAttributeGateway.class, pdcAttributeGateway);
        services.put(ForgeItemRefreshService.class, itemRefreshService);
        services.put(ForgeService.class, forgeService);
        services.put(ForgeGuiService.class, forgeGuiService);
        services.put(RecipeBookGuiService.class, recipeBookGuiService);
        services.put(EditorGuiService.class, editorGuiService);
        return Map.copyOf(services);
    }
}
