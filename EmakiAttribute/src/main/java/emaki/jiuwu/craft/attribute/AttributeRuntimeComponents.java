package emaki.jiuwu.craft.attribute;

import java.util.LinkedHashMap;
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
        PdcAttributeApi pdcAttributeApi,
        AttributeService attributeService,
        AttributeListener listener,
        AttributeCommand command,
        MythicBridge mythicBridge) implements RuntimeComponents {

    @Override
    public Map<Class<?>, Object> services() {
        Map<Class<?>, Object> services = new LinkedHashMap<>();
        services.put(AttributeRegistry.class, attributeRegistry);
        services.put(AttributeBalanceRegistry.class, attributeBalanceRegistry);
        services.put(DamageTypeRegistry.class, damageTypeRegistry);
        services.put(DefaultProfileRegistry.class, defaultProfileRegistry);
        services.put(LoreFormatRegistry.class, loreFormatRegistry);
        services.put(AttributePresetRegistry.class, presetRegistry);
        services.put(PdcReadRuleLoader.class, pdcReadRuleLoader);
        services.put(LanguageLoader.class, languageLoader);
        services.put(MessageService.class, messageService);
        services.put(PdcAttributeApi.class, pdcAttributeApi);
        services.put(AttributeService.class, attributeService);
        services.put(AttributeListener.class, listener);
        services.put(AttributeCommand.class, command);
        services.put(MythicBridge.class, mythicBridge);
        services.entrySet().removeIf(entry -> entry.getValue() == null);
        return Map.copyOf(services);
    }
}
