package emaki.jiuwu.craft.corelib.action.builtin.source;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseSource;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;

public final class PlayersWithMaterialSource extends BaseSource {

    public PlayersWithMaterialSource() {
        super("players_with_material", "背包中持有指定材质物品的在线玩家。",
                CoreActionExecutionDomain.SERVER_GLOBAL,
                CoreStageParameter.required("material", CoreStageParameterType.MATERIAL, "物品材质"),
                CoreStageParameter.optional("limit", CoreStageParameterType.INTEGER, "0",
                        "最大玩家数，0 表示不限制"));
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Material material = StageSupport.material(arguments.getString("material"));
        if (material == null) {
            return CoreSourceResult.invalid("action.source.players_with_material.unknown_material",
                    java.util.Map.of("material", arguments.getString("material")));
        }
        int limit = Math.max(0, arguments.getInt("limit", 0));
        List<CoreActionSubject> subjects = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (limit > 0 && subjects.size() >= limit) {
                break;
            }
            if (player.getInventory().contains(material)) {
                subjects.add(CoreActionSubject.of(player));
            }
        }
        if (subjects.isEmpty()) {
            return CoreSourceResult.empty("action.source.players_with_material.no_match");
        }
        return CoreSourceResult.selected(subjects);
    }
}
