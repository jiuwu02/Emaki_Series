package emaki.jiuwu.craft.corelib.action.builtin.source;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;

public final class RandomSource extends BaseSource {

    public RandomSource() {
        super("random", "从在线玩家中随机抽取若干目标。",
                CoreActionExecutionDomain.SERVER_GLOBAL,
                CoreStageParameter.optional("count", CoreStageParameterType.INTEGER, "1",
                        "随机抽取的目标数"));
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        int count = Math.max(1, arguments.getInt("count", 1));
        List<Entity> pool = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (pool.isEmpty()) {
            return CoreSourceResult.empty("action.source.random.no_pool");
        }
        List<CoreActionSubject> subjects = new ArrayList<>();
        for (int index = 0; index < count && !pool.isEmpty(); index++) {
            int pick = ThreadLocalRandom.current().nextInt(pool.size());
            subjects.add(CoreActionSubject.of(pool.remove(pick)));
        }
        return CoreSourceResult.selected(subjects);
    }
}
