package emaki.jiuwu.craft.item.script;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.inventory.ItemStack;
import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.script.host.ScriptCallbackException;
import emaki.jiuwu.craft.corelib.script.host.ScriptCallbackRunner;
import emaki.jiuwu.craft.item.api.effect.ItemEffect;
import emaki.jiuwu.craft.item.api.effect.ItemEffectApplyContext;
import emaki.jiuwu.craft.item.api.effect.ItemEffectParseContext;
import emaki.jiuwu.craft.item.api.effect.ItemEffectType;

final class ScriptItemEffectType implements ItemEffectType {

    private final String id;
    private final Value parseFn;
    private final Value clearFn;
    private final Value applyFn;
    private final ScriptCallbackRunner runner;
    private final Logger logger;

    ScriptItemEffectType(ItemScriptPayload payload, ScriptCallbackRunner runner, Logger logger) {
        this.id = payload.id();
        this.parseFn = payload.parseFn();
        this.clearFn = payload.clearFn();
        this.applyFn = payload.applyFn();
        this.runner = runner;
        this.logger = logger;
    }

    @Override
    public @NotNull String typeId() {
        return id;
    }

    @Override
    public @Nullable ItemEffect parse(@NotNull ItemEffectParseContext context) {
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("definitionId", context.definitionId());
        export.put("config", context.effect());
        try {
            Value effect = runner.run(parseFn, export);
            if (ItemScriptPayloads.isAbsent(effect)) {
                return null;
            }
            return new ScriptItemEffect(this, effect);
        } catch (ScriptCallbackException exception) {
            logger.warning("[script] 脚本效果 '" + id + "' 解析物品 '"
                    + context.definitionId() + "' 失败: " + exception.getMessage());
            return null;
        }
    }

    void clear(Value effect, ItemEffectApplyContext context) {
        if (clearFn == null) {
            return;
        }
        try {
            runner.run(clearFn, effect, exportApplyContext(context));
        } catch (ScriptCallbackException exception) {
            logger.warning("[script] 脚本效果 '" + id + "' 清理物品 '"
                    + context.definitionId() + "' 失败: " + exception.getMessage());
        }
    }

    void apply(Value effect, ItemEffectApplyContext context) {
        try {
            runner.run(applyFn, effect, exportApplyContext(context));
        } catch (ScriptCallbackException exception) {
            logger.warning("[script] 脚本效果 '" + id + "' 应用物品 '"
                    + context.definitionId() + "' 失败: " + exception.getMessage());
        }
    }

    private Map<String, Object> exportApplyContext(ItemEffectApplyContext context) {
        ItemStack itemStack = context.itemStack();
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("itemStack", itemStack);
        export.put("variables", context.variables());
        export.put("equipSlot", context.equipSlot());
        return export;
    }
}
