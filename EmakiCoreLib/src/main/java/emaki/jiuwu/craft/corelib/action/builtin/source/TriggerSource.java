package emaki.jiuwu.craft.corelib.action.builtin.source;

import java.util.List;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionKeys;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * The entity named by the trigger, for triggers whose subject differs from the caster.
 *
 * <p>The trigger key carries a name or UUID rather than an entity reference, because a pipeline may be
 * compiled long before it runs and holding a live entity in a context key would keep it from being
 * collected.</p>
 *
 * <p>Domain {@code SERVER_GLOBAL}: reads a string context key and looks it up in the online-player
 * table. Both are global structures, so no owner thread is implied.</p>
 */
public final class TriggerSource extends BaseSource {

    public TriggerSource() {
        super("trigger", "The entity named by the trigger context.",
                CoreActionExecutionDomain.SERVER_GLOBAL);
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Optional<String> trigger = context.get(CoreActionKeys.TRIGGER);
        if (trigger.isEmpty() || Texts.isBlank(trigger.get())) {
            return CoreSourceResult.empty("action.source.trigger.no_trigger");
        }
        Player player = Bukkit.getPlayerExact(Texts.trim(trigger.get()));
        if (player == null) {
            return CoreSourceResult.empty("action.source.trigger.not_online");
        }
        return CoreSourceResult.selected(List.of(CoreActionSubject.of(player)));
    }
}
