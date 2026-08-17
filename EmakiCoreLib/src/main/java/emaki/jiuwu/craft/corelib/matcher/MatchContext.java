package emaki.jiuwu.craft.corelib.matcher;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.variable.VariableContext;

/**
 * Matcher 匹配上下文。包含被测物品、玩家、目标物品（用于比较）、物品源引用和变量上下文。
 */
public record MatchContext(
        @Nullable ItemStack item,
        @Nullable ItemSourceRef itemSource,
        @Nullable Player player,
        @Nullable ItemStack targetItem,
        @Nullable ItemSourceRef targetItemSource,
        @NotNull VariableContext variableContext) {

    public MatchContext {
        if (variableContext == null) {
            variableContext = VariableContext.builder(player).build();
        }
    }

    /**
     * 创建简单的匹配上下文（仅包含物品和玩家）。
     *
     * @param item       被测物品
     * @param itemSource 物品源引用（如果已知）
     * @param player     玩家
     * @return MatchContext
     */
    public static @NotNull MatchContext of(@Nullable ItemStack item, @Nullable ItemSourceRef itemSource, @Nullable Player player) {
        return new MatchContext(item, itemSource, player, null, null, VariableContext.builder(player).build());
    }

    /**
     * 创建包含目标物品的匹配上下文（用于 compare_target 类型）。
     *
     * @param item             被测物品
     * @param itemSource       被测物品源引用
     * @param player           玩家
     * @param targetItem       目标物品
     * @param targetItemSource 目标物品源引用
     * @return MatchContext
     */
    public static @NotNull MatchContext withTarget(
            @Nullable ItemStack item,
            @Nullable ItemSourceRef itemSource,
            @Nullable Player player,
            @Nullable ItemStack targetItem,
            @Nullable ItemSourceRef targetItemSource) {
        return new MatchContext(item, itemSource, player, targetItem, targetItemSource, VariableContext.builder(player).build());
    }

    /**
     * 创建包含自定义变量上下文的匹配上下文。
     *
     * @param item             被测物品
     * @param itemSource       物品源引用
     * @param player           玩家
     * @param targetItem       目标物品
     * @param targetItemSource 目标物品源引用
     * @param variableContext  变量上下文
     * @return MatchContext
     */
    public static @NotNull MatchContext with(
            @Nullable ItemStack item,
            @Nullable ItemSourceRef itemSource,
            @Nullable Player player,
            @Nullable ItemStack targetItem,
            @Nullable ItemSourceRef targetItemSource,
            @NotNull VariableContext variableContext) {
        return new MatchContext(item, itemSource, player, targetItem, targetItemSource, variableContext);
    }
}
