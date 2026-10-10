package emaki.jiuwu.craft.item.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.api.effect.ItemEffect;
import emaki.jiuwu.craft.item.api.effect.ItemEffectApplyContext;
import emaki.jiuwu.craft.item.api.effect.ItemEffectType;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;

public final class EmakiItemEffectApplier {

    private final EmakiItemEffectRegistry registry;
    private final Logger logger;

    public EmakiItemEffectApplier(EmakiItemEffectRegistry registry, Logger logger) {
        this.registry = registry;
        this.logger = logger;
    }

    public void apply(ItemStack itemStack, EmakiItemDefinition definition, Map<String, Object> variables) {
        if (registry == null || itemStack == null || definition == null) {
            return;
        }
        List<Map<String, Object>> effects = definition.customEffects();
        if (effects.isEmpty()) {
            return;
        }
        Map<String, Object> safeVariables = variables == null ? Map.of() : variables;
        for (Map<String, Object> effectConfig : effects) {
            applyEffect(itemStack, definition, effectConfig, safeVariables);
        }
    }

    private void applyEffect(ItemStack itemStack,
            EmakiItemDefinition definition,
            Map<String, Object> effectConfig,
            Map<String, Object> variables) {
        String typeId = Texts.normalizeId(Texts.toStringSafe(effectConfig.get("type")));
        ItemEffectType type = registry.find(typeId);
        if (type == null) {
            return;
        }
        try {
            ItemEffectApplyContext context = new EffectApplyContext(
                    definition.id(),
                    effectConfig,
                    variables,
                    definition.equipSlot(),
                    itemStack
            );
            ItemEffect effect = type.parse(context);
            if (effect == null) {
                return;
            }
            effect.clear(context);
            effect.apply(context);
        } catch (RuntimeException | LinkageError exception) {
            logger.warning("[effect] 自定义效果 '" + typeId + "' (物品 '" + definition.id()
                    + "')执行失败: " + exception.getMessage());
        }
    }

    private record EffectApplyContext(String definitionId,
            Map<String, Object> effect,
            Map<String, Object> variables,
            String equipSlot,
            ItemStack itemStack) implements ItemEffectApplyContext {

        private EffectApplyContext {
            effect = effect == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(effect));
            variables = variables == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        }
    }
}
