package emaki.jiuwu.craft.item.script;

import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.item.api.effect.ItemEffect;
import emaki.jiuwu.craft.item.api.effect.ItemEffectApplyContext;

final class ScriptItemEffect implements ItemEffect {

    private final ScriptItemEffectType type;
    private final Value effect;

    ScriptItemEffect(ScriptItemEffectType type, Value effect) {
        this.type = type;
        this.effect = effect;
    }

    @Override
    public void clear(@NotNull ItemEffectApplyContext context) {
        type.clear(effect, context);
    }

    @Override
    public void apply(@NotNull ItemEffectApplyContext context) {
        type.apply(effect, context);
    }
}
