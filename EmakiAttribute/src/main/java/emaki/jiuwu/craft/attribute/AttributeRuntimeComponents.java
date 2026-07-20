package emaki.jiuwu.craft.attribute;

import java.util.List;
import java.util.Map;

import org.bukkit.event.Listener;

import emaki.jiuwu.craft.attribute.api.PdcAttributeApi;
import emaki.jiuwu.craft.attribute.bridge.MythicBridge;
import emaki.jiuwu.craft.attribute.command.AttributeCommand;
import emaki.jiuwu.craft.attribute.loader.AttributeBalanceRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributePresetRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributeRegistry;
import emaki.jiuwu.craft.attribute.loader.DamageTypeRegistry;
import emaki.jiuwu.craft.attribute.loader.DefaultProfileRegistry;
import emaki.jiuwu.craft.attribute.loader.LanguageLoader;
import emaki.jiuwu.craft.attribute.loader.LoreFormatRegistry;
import emaki.jiuwu.craft.attribute.loader.PdcReadRuleLoader;
import emaki.jiuwu.craft.attribute.service.AttributePointsGuiService;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.attribute.service.MessageService;
import emaki.jiuwu.craft.attribute.service.ParentAttributeDataStore;
import emaki.jiuwu.craft.attribute.service.ParentAttributeService;
import emaki.jiuwu.craft.corelib.api.integration.EmakiAttributeBridge;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.runtime.RuntimeComponents;

record AttributeRuntimeComponents(ExecutionDispatcher executionDispatcher,
        ThreadOwnership threadOwnership,
        AttributeRegistry attributeRegistry,
        AttributeBalanceRegistry attributeBalanceRegistry,
        DamageTypeRegistry damageTypeRegistry,
        DefaultProfileRegistry defaultProfileRegistry,
        LoreFormatRegistry loreFormatRegistry,
        AttributePresetRegistry presetRegistry,
        PdcReadRuleLoader pdcReadRuleLoader,
        LanguageLoader languageLoader,
        MessageService messageService,
        EmakiAttributeBridge emakiAttributeBridge,
        PdcAttributeApi.Bridge pdcAttributeApi,
        ParentAttributeDataStore parentAttributeDataStore,
        ParentAttributeService parentAttributeService,
        GuiTemplateLoader guiTemplateLoader,
        GuiService guiService,
        AttributePointsGuiService attributePointsGuiService,
        AttributeService attributeService,
        List<Listener> listeners,
        AttributeCommand command,
        MythicBridge mythicBridge) implements RuntimeComponents {

    @Override
    public Map<Class<?>, Object> services() {
        return RuntimeComponents.services(
                RuntimeComponents.component(ExecutionDispatcher.class, executionDispatcher),
                RuntimeComponents.component(ThreadOwnership.class, threadOwnership),
                RuntimeComponents.component(AttributeRegistry.class, attributeRegistry),
                RuntimeComponents.component(AttributeBalanceRegistry.class, attributeBalanceRegistry),
                RuntimeComponents.component(DamageTypeRegistry.class, damageTypeRegistry),
                RuntimeComponents.component(DefaultProfileRegistry.class, defaultProfileRegistry),
                RuntimeComponents.component(LoreFormatRegistry.class, loreFormatRegistry),
                RuntimeComponents.component(AttributePresetRegistry.class, presetRegistry),
                RuntimeComponents.component(PdcReadRuleLoader.class, pdcReadRuleLoader),
                RuntimeComponents.component(LanguageLoader.class, languageLoader),
                RuntimeComponents.component(MessageService.class, messageService),
                RuntimeComponents.component(EmakiAttributeBridge.class, emakiAttributeBridge),
                RuntimeComponents.component(PdcAttributeApi.Bridge.class, pdcAttributeApi),
                RuntimeComponents.component(emaki.jiuwu.craft.corelib.api.integration.PdcAttributeApi.class, pdcAttributeApi),
                RuntimeComponents.component(ParentAttributeDataStore.class, parentAttributeDataStore),
                RuntimeComponents.component(ParentAttributeService.class, parentAttributeService),
                RuntimeComponents.component(GuiTemplateLoader.class, guiTemplateLoader),
                RuntimeComponents.component(GuiService.class, guiService),
                RuntimeComponents.component(AttributePointsGuiService.class, attributePointsGuiService),
                RuntimeComponents.component(AttributeService.class, attributeService),
                RuntimeComponents.component(AttributeCommand.class, command),
                RuntimeComponents.component(MythicBridge.class, mythicBridge)
        );
    }
}
