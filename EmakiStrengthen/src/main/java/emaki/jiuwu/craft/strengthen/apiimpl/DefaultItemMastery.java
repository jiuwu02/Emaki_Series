package emaki.jiuwu.craft.strengthen.apiimpl;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.ItemMastery;
import emaki.jiuwu.craft.strengthen.api.model.ItemMasteryView;
import emaki.jiuwu.craft.strengthen.api.target.EnhancementTargetProvider;
import emaki.jiuwu.craft.strengthen.enhancement.target.EnhancementTargetRegistry;

public final class DefaultItemMastery implements ItemMastery {

    private final EmakiStrengthenPlugin plugin;

    public DefaultItemMastery(EmakiStrengthenPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull EmakiResult<ItemMasteryView> snapshot(@Nullable Player player,
            @Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return EmakiResult.invalidInput("strengthen.error.no_target");
        }
        EnhancementTargetRegistry registry = plugin.enhancementTargetRegistry();
        if (registry == null || !plugin.contentReady()) {
            return EmakiResult.unavailable();
        }
        try {
            EnhancementTargetProvider provider = registry.resolve(player, itemStack);
            if (provider == null) {
                return EmakiResult.notFound("strengthen.enhancement.provider_not_found");
            }
            EmakiResult<ItemMasteryView> snapshot = provider.masterySnapshot(player, itemStack);
            return snapshot == null
                    ? EmakiResult.internalError("strengthen.error.state_read_failed")
                    : snapshot;
        } catch (RuntimeException | LinkageError exception) {
            return EmakiResult.internalError("strengthen.error.state_read_failed");
        }
    }
}
