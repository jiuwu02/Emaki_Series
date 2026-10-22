package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.item.MinecraftServerVersions;

public final class SetBedSpawnStage extends BaseStage {

    public SetBedSpawnStage() {
        super("set_bed_spawn", "player", "把玩家重生点设置为管道原点。",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY);
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (!(context.currentTarget().entityOrNull() instanceof Player player)) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        Location origin;
        try {
            origin = context.origin();
        } catch (IllegalStateException exception) {
            return CoreActionOutcome.skipped("action.stage.set_bed_spawn.no_origin");
        }
        if (origin == null || origin.getWorld() == null) {
            return CoreActionOutcome.skipped("action.stage.set_bed_spawn.no_origin");
        }
        if (MinecraftServerVersions.atLeast("26")) {
            // 26.x 的 setBedSpawnLocation 落点异常（respawn 复合键写成世界出生点），
            // 改派发原版 spawnpoint 到原点所在维度；其 pos 参数只收整数方块坐标
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "execute in " + origin.getWorld().getKey() + " run spawnpoint " + player.getName()
                            + " " + origin.getBlockX() + " " + origin.getBlockY() + " " + origin.getBlockZ());
        } else {
            player.setBedSpawnLocation(origin, true);
        }
        return CoreActionOutcome.success();
    }
}
