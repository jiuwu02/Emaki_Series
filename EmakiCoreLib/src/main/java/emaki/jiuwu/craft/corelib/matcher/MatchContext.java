package emaki.jiuwu.craft.corelib.matcher;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.variable.VariableContext;

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

    public static @NotNull MatchContext of(@Nullable ItemStack item, @Nullable ItemSourceRef itemSource, @Nullable Player player) {
        return new MatchContext(item, itemSource, player, null, null, VariableContext.builder(player).build());
    }

    public static @NotNull MatchContext withTarget(
            @Nullable ItemStack item,
            @Nullable ItemSourceRef itemSource,
            @Nullable Player player,
            @Nullable ItemStack targetItem,
            @Nullable ItemSourceRef targetItemSource) {
        return new MatchContext(item, itemSource, player, targetItem, targetItemSource, VariableContext.builder(player).build());
    }

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
