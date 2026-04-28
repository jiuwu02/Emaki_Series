package emaki.jiuwu.craft.attribute;

import java.util.Map;

import emaki.jiuwu.craft.attribute.api.PdcAttributeApi;
import emaki.jiuwu.craft.attribute.bridge.MythicBridge;
import emaki.jiuwu.craft.attribute.command.AttributeCommand;
import emaki.jiuwu.craft.attribute.listener.AttributeListener;
import emaki.jiuwu.craft.attribute.loader.AttributeBalanceRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributePresetRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributeRegistry;
import emaki.jiuwu.craft.attribute.loader.DamageTypeRegistry;
import emaki.jiuwu.craft.attribute.loader.DefaultProfileRegistry;
import emaki.jiuwu.craft.attribute.loader.LanguageLoader;
import emaki.jiuwu.craft.attribute.loader.LoreFormatRegistry;
import emaki.jiuwu.craft.attribute.loader.PdcReadRuleLoader;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.attribute.service.MessageService;
import emaki.jiuwu.craft.corelib.integration.EmakiAttributeBridge;
import emaki.jiuwu.craft.corelib.runtime.RuntimeComponents;

record AttributeRuntimeComponents(AttributeRegistry attributeRegistry,
        AttributeBalanceRegistry attributeBalanceRegistry,
        DamageTypeRegistry damageTypeRegistry,
        DefaultProfileRegistry defaultProfileRegistry,
        LoreFormatRegistry loreFormatRegistry,
        AttributePresetRegistry presetRegistry,
        PdcReadRuleLoader pdcReadRuleLoader,
        LanguageLoader languageLoader,
        MessageService messageService,
        EmakiAttributeBridge emakiAttributeBridge,
        PdcAttributeApi pdcAttributeApi,
        AttributeService attributeService,
        AttributeListener listener,
        AttributeCommand command,
        MythicBridge mythicBridge) implements RuntimeComponents {

    @Override
    public Map<Class<?>, Object> services() {
        return RuntimeComponents.services(
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
                RuntimeComponents.component(PdcAttributeApi.class, pdcAttributeApi),
                RuntimeComponents.component(emaki.jiuwu.craft.corelib.integration.PdcAttributeApi.class, pdcAttributeApi),
                RuntimeComponents.component(AttributeService.class, attributeService),
                RuntimeComponents.component(AttributeListener.class, listener),
                RuntimeComponents.component(AttributeCommand.class, command),
                RuntimeComponents.component(MythicBridge.class, mythicBridge)
        );
    }
}
